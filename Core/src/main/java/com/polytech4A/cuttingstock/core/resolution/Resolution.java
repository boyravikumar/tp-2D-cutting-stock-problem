/*
 *
 *  * Project to resolve 2D cutting stock problem for Discreet Optimizations course at Polytech Lyon
 *  * Copyright (C) 2015.  CARON Antoine and CHAUSSENDE Adrien
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU Affero General Public License as
 *  * published by the Free Software Foundation, either version 3 of the
 *  * License, or (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU Affero General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU Affero General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.polytech4A.cuttingstock.core.resolution;

import com.polytech4A.cuttingstock.core.method.LinearResolutionMethod;
import com.polytech4A.cuttingstock.core.model.Box;
import com.polytech4A.cuttingstock.core.model.Pattern;
import com.polytech4A.cuttingstock.core.model.Solution;
import com.polytech4A.cuttingstock.core.packing.GuillotineSortBAF;
import com.polytech4A.cuttingstock.core.packing.MaxRectsBAF;
import com.polytech4A.cuttingstock.core.packing.Packer;
import com.polytech4A.cuttingstock.core.resolution.util.context.Context;
import com.polytech4A.cuttingstock.core.resolution.util.context.ContextLoaderUtils;
import com.polytech4A.cuttingstock.core.resolution.util.context.IllogicalContextException;
import com.polytech4A.cuttingstock.core.resolution.util.context.MalformedContextFileException;
import com.polytech4A.cuttingstock.core.save.Save;
import com.polytech4A.cuttingstock.core.save.ToImg;
import com.polytech4A.cuttingstock.core.solver.SimulatedAnnealing;
import com.polytech4A.cuttingstock.core.solver.Solver;
import com.polytech4A.cuttingstock.core.solver.neighbour.*;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Antoine on 12/03/2015.
 *
 * @author Antoine
 * @version 1.0
 *          <p>
 *          Representation of a resolution for the 2d cutting stock problem.
 */
public class Resolution {

    /**
     * Logger.
     */
    private static final Logger logger = Logger.getLogger(Resolution.class);

    /**
     * Context of the resolution.
     */
    private Context context;

    /**
     * Path of the contextFile.
     */
    private String contextFilePath;

    /**
     * Solver for the problem.
     */
    private Solver solver;

    /**
     * Saving methods.
     */
    private List<Save> saveMethods;

    /**
     *
     */
    private ArrayList<Comparator<Box>> boxComparators;

    public Resolution(String contextFilePath) {
        this.contextFilePath = contextFilePath;
        saveMethods = new ArrayList<>();
        saveMethods.add(new ToImg());
        boxComparators = new ArrayList<>();
        boxComparators.add(Box.Comparators.AREA);
        boxComparators.add(Box.Comparators.X);
        boxComparators.add(Box.Comparators.Y);
    }

    /**
     * Load context function.
     *
     * @param path Path to the Context file.
     * @return Context instance.
     * @throws java.io.IOException                                                                    If the file opening throw exception.
     * @throws com.polytech4A.cuttingstock.core.resolution.util.context.MalformedContextFileException If the file don't have the good structure.
     * @throws IllogicalContextException                                                              if an image is bigger than pattern size defined in the file.
     */
    public static Context loadContext(String path) throws IOException, MalformedContextFileException, IllogicalContextException {
        return ContextLoaderUtils.loadContext(path);
    }

    /**
     * Generate the first Solution from a NotNull Context in attribut.
     *
     * @return Solution with pattern and Boxes.
     * @see com.polytech4A.cuttingstock.core.resolution.util.context.Context
     * @see com.polytech4A.cuttingstock.core.model.Pattern
     * @see com.polytech4A.cuttingstock.core.model.Box
     */
    public static Solution generateFirstSolution(Context context) {
        double nbPatterns = context.getBoxes().size();
        ArrayList<Pattern> patterns = new ArrayList<>();
        ArrayList<Box> boxes = new ArrayList<>();
        for (Box b : context.getBoxes()) {
            boxes.add(b.clone());
        }
        boxes.parallelStream().forEach(b -> b.setAmount(0));
        Pattern p = new Pattern(context.getPatternSize(), boxes);
        for (int i = 0; i < nbPatterns; i++) {
            Pattern clP = p.clone();
            clP.getAmounts().get(i).setAmount(1);
            patterns.add(clP);
        }
        return new Solution(patterns);
    }

    /**
     * Function to solve the 2D Cutting Stock Problem on a Resolution.
     *
     * @param nbofIterations nb of iteration for the solver.
     * @param intPacker integer that defined the type of packer that will be used.
     */
    public void solve(Integer nbofIterations, Integer intPacker) {
        Packer packer = null;
        switch (intPacker) {
            case 1:
                packer = new GuillotineSortBAF(boxComparators);
                logger.info("Parameters - Packing : Guillotine Sort BAF");
                break;
            case 2:
                packer = new MaxRectsBAF(boxComparators);
                logger.info("Parameters - Packing : Maximum Rectangles Sort BAF");
                break;
            default:
                packer = new GuillotineSortBAF(boxComparators);
                logger.info("Parameters - Packing : Guillotine Sort BAF");
                break;
        }
        Solution startingSolution = getStartingSolution(packer);
        logger.info("FirstSolution ----->" + startingSolution);
        ArrayList<INeighbourUtils> generators = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            generators.add(new DynPatternIncrementNeighbour());
            generators.add(new DynPatternMoveNeighbour());
        }
        generators.add(new RemovePatternNeighbour());
        solver = new SimulatedAnnealing(
                packer,
                new LinearResolutionMethod(context),
                generators,
                nbofIterations
        );
        Solution bestFound = solver.getSolution(startingSolution, context);
        logger.info("Best Solution found :" + bestFound);
        saveMethods.parallelStream().forEach(s -> s.save(context.getLabel(), bestFound));
    }

    /**
     * Generate the startingSolution for solving.
     *
     * @return real random packable solution.
     */
    private Solution getStartingSolution(Packer packer) {
        try {
            context = loadContext(contextFilePath);
            ArrayList<INeighbourUtils> generators = new ArrayList<>();
            generators.add(new IncrementNeighbour());
            generators.add(new MoveNeighbour());
            generators.add(new RemovePatternNeighbour());
            SimulatedAnnealing realSolutionGenerator = new SimulatedAnnealing(
                    packer,
                    new LinearResolutionMethod(context),
                    generators,
                    10000);
            Solution firstSolution = generateFirstSolution(context);
            return realSolutionGenerator.getRandomSolution(firstSolution, true);
        } catch (IOException e) {
            logger.error(e);
        } catch (MalformedContextFileException e) {
            logger.error(e);
        } catch (IllogicalContextException e) {
            logger.error(e);
        }
        return null;
    }
}
