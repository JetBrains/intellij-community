package org.hanuna.gitalk.graphmodel.fragment;

import org.hanuna.gitalk.common.Function;
import org.hanuna.gitalk.graph.GraphTestUtils;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.mutable.MutableGraph;
import org.hanuna.gitalk.graphmodel.GraphModel;
import org.hanuna.gitalk.graphmodel.impl.GraphModelImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static junit.framework.Assert.assertEquals;
import static org.hanuna.gitalk.graph.GraphStrUtils.toStr;

/**
 * @author erokhins
 */
public class GraphModelTest {

    private GraphModel simpleGraph;
    private GraphModel middleGraph;
    private GraphModel hardGraph;


    @NotNull
    public GraphModel buildGraphModel(@NotNull String inputGraph) {
        MutableGraph graph = GraphTestUtils.getNewMutableGraph(inputGraph);
        return new GraphModelImpl(graph);
    }


    /**
     * a0
     *  |
     * a1
     *  |
     * a2
     *  |
     *  | a3
     *  |  |
     *  | a4
     *  |  |
     *  | a5
     *  | /
     *  a6
     *  |
     *  a7
     *  |
     *  a8
     *  |
     *  a9
     */


    @Before
    public void initMiddleGraph() {
        middleGraph = buildGraphModel(
                "a0|-a1\n" +
                        "a1|-a2\n" +
                        "a2|-a6\n" +
                        "a3|-a4\n" +
                        "a4|-a5\n" +
                        "a5|-a6\n" +
                        "a6|-a7\n" +
                        "a7|-a8\n" +
                        "a8|-a9\n" +
                        "a9|-"
        );
        assertEquals("init graph",
                toStr(middleGraph.getGraph()),

                "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a0|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
                "a2|-a1:a2:USUAL:a0|-a2:a6:USUAL:a0|-COMMIT_NODE|-a0|-2\n" +
                "a3|-|-a3:a4:USUAL:a3|-COMMIT_NODE|-a3|-3\n" +
                "a4|-a3:a4:USUAL:a3|-a4:a5:USUAL:a3|-COMMIT_NODE|-a3|-4\n" +
                "a5|-a4:a5:USUAL:a3|-a5:a6:USUAL:a3|-COMMIT_NODE|-a3|-5\n" +
                "a6|-a2:a6:USUAL:a0 a5:a6:USUAL:a3|-a6:a7:USUAL:a0|-COMMIT_NODE|-a0|-6\n" +
                "a7|-a6:a7:USUAL:a0|-a7:a8:USUAL:a0|-COMMIT_NODE|-a0|-7\n" +
                "a8|-a7:a8:USUAL:a0|-a8:a9:USUAL:a0|-COMMIT_NODE|-a0|-8\n" +
                "a9|-a8:a9:USUAL:a0|-|-COMMIT_NODE|-a0|-9"
        );
    }






    /**
     * a0
     *  |
     * a1
     *  |
     * a2
     *  |\
     *  | | a3
     *  | |  |
     *  | | a4
     *  | |  |
     *  | | a5
     *  | \ /
     *  | a6
     *  |  |
     *  | a7
     *  |  |
     *  | a8
     *  | /
     *  *  a9
     *  | /
     *  *  a10
     *  |   |
     *  |  a11
     *  |   |
     *  |  a12
     *  |   /
     *   a13
     *    |
     *   a14
     *    |
     *   a15
     *
     */


    @Before
    public void initHardGraph() {
        hardGraph = buildGraphModel(
                "a0|-a1\n" +
                "a1|-a2\n" +
                "a2|-a13 a6\n" +
                "a3|-a4\n" +
                "a4|-a5\n" +
                "a5|-a6\n" +
                "a6|-a7\n" +
                "a7|-a8\n" +
                "a8|-a13\n" +
                "a9|-a13\n" +
                "a10|-a11\n" +
                "a11|-a12\n" +
                "a12|-a13\n" +
                "a13|-a14\n" +
                "a14|-a15\n" +
                "a15|-"
        );
        assertEquals("init graph",

                "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a0|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
                "a2|-a1:a2:USUAL:a0|-a2:a13:USUAL:a2#a13 a2:a6:USUAL:a2#a6|-COMMIT_NODE|-a0|-2\n" +
                "a3|-|-a3:a4:USUAL:a3|-COMMIT_NODE|-a3|-3\n" +
                "a4|-a3:a4:USUAL:a3|-a4:a5:USUAL:a3|-COMMIT_NODE|-a3|-4\n" +
                "a5|-a4:a5:USUAL:a3|-a5:a6:USUAL:a3|-COMMIT_NODE|-a3|-5\n" +
                "a6|-a2:a6:USUAL:a2#a6 a5:a6:USUAL:a3|-a6:a7:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-6\n" +
                "a7|-a6:a7:USUAL:a2#a6|-a7:a8:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-7\n" +
                "a8|-a7:a8:USUAL:a2#a6|-a8:a13:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-8\n" +
                "a13|-a2:a13:USUAL:a2#a13 a8:a13:USUAL:a2#a6|-a13:a13:USUAL:a2#a13|-EDGE_NODE|-a2#a13|-9\n" +
                "   a9|-|-a9:a13:USUAL:a9|-COMMIT_NODE|-a9|-9\n" +
                "a10|-|-a10:a11:USUAL:a10|-COMMIT_NODE|-a10|-10\n" +
                "   a13|-a13:a13:USUAL:a2#a13 a9:a13:USUAL:a9|-a13:a13:USUAL:a2#a13|-EDGE_NODE|-a2#a13|-10\n" +
                "a11|-a10:a11:USUAL:a10|-a11:a12:USUAL:a10|-COMMIT_NODE|-a10|-11\n" +
                "a12|-a11:a12:USUAL:a10|-a12:a13:USUAL:a10|-COMMIT_NODE|-a10|-12\n" +
                "a13|-a12:a13:USUAL:a10 a13:a13:USUAL:a2#a13|-a13:a14:USUAL:a2#a13|-COMMIT_NODE|-a2#a13|-13\n" +
                "a14|-a13:a14:USUAL:a2#a13|-a14:a15:USUAL:a2#a13|-COMMIT_NODE|-a2#a13|-14\n" +
                "a15|-a14:a15:USUAL:a2#a13|-|-COMMIT_NODE|-a2#a13|-15",

                toStr(hardGraph.getGraph())
        );
    }


    private void runTestGraphBranchesVisibility(@NotNull GraphModel graphModel,
                                               @NotNull final Set<String> startedNodes, @NotNull String outStr) {

        graphModel.setVisibleBranchesNodes(new Function<Node, Boolean>() {
            @NotNull
            @Override
            public Boolean get(@NotNull Node key) {
                return startedNodes.contains(key.getCommitHash().toStrHash());
            }
        });
        assertEquals(outStr, toStr(graphModel.getGraph()));
    }

    @Test
    public void middle1() {
        Set<String> startNodes = new HashSet<String>();
        startNodes.add("a0");

        runTestGraphBranchesVisibility(
                middleGraph,

                startNodes,

                "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
                        "a1|-a0:a1:USUAL:a0|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
                        "a2|-a1:a2:USUAL:a0|-a2:a6:USUAL:a0|-COMMIT_NODE|-a0|-2\n" +
                        "a6|-a2:a6:USUAL:a0|-a6:a7:USUAL:a0|-COMMIT_NODE|-a0|-3\n" +
                        "a7|-a6:a7:USUAL:a0|-a7:a8:USUAL:a0|-COMMIT_NODE|-a0|-4\n" +
                        "a8|-a7:a8:USUAL:a0|-a8:a9:USUAL:a0|-COMMIT_NODE|-a0|-5\n" +
                        "a9|-a8:a9:USUAL:a0|-|-COMMIT_NODE|-a0|-6");

    }

    @Test
    public void middle2() {
        Set<String> startNodes = new HashSet<String>();
        startNodes.add("a1");

        runTestGraphBranchesVisibility(
                middleGraph,

                startNodes,

                "a1|-|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
                "a2|-a1:a2:USUAL:a0|-a2:a6:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
                "a6|-a2:a6:USUAL:a0|-a6:a7:USUAL:a0|-COMMIT_NODE|-a0|-2\n" +
                "a7|-a6:a7:USUAL:a0|-a7:a8:USUAL:a0|-COMMIT_NODE|-a0|-3\n" +
                "a8|-a7:a8:USUAL:a0|-a8:a9:USUAL:a0|-COMMIT_NODE|-a0|-4\n" +
                "a9|-a8:a9:USUAL:a0|-|-COMMIT_NODE|-a0|-5"
        );

    }

    @Test
    public void middleTwoBranch() {
        Set<String> startNodes = new HashSet<String>();
        startNodes.add("a1");
        startNodes.add("a5");

        runTestGraphBranchesVisibility(
                middleGraph,

                startNodes,
               "a1|-|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
               "a2|-a1:a2:USUAL:a0|-a2:a6:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
               "a5|-|-a5:a6:USUAL:a3|-COMMIT_NODE|-a3|-2\n" +
               "a6|-a2:a6:USUAL:a0 a5:a6:USUAL:a3|-a6:a7:USUAL:a0|-COMMIT_NODE|-a0|-3\n" +
               "a7|-a6:a7:USUAL:a0|-a7:a8:USUAL:a0|-COMMIT_NODE|-a0|-4\n" +
               "a8|-a7:a8:USUAL:a0|-a8:a9:USUAL:a0|-COMMIT_NODE|-a0|-5\n" +
               "a9|-a8:a9:USUAL:a0|-|-COMMIT_NODE|-a0|-6"
        );

    }



    //////////////Hard



    @Test
    public void hard1() {
        Set<String> startNodes = new HashSet<String>();
        startNodes.add("a0");

        runTestGraphBranchesVisibility(
                hardGraph,

                startNodes,

                "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a0|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
                "a2|-a1:a2:USUAL:a0|-a2:a13:USUAL:a2#a13 a2:a6:USUAL:a2#a6|-COMMIT_NODE|-a0|-2\n" +
                "a6|-a2:a6:USUAL:a2#a6|-a6:a7:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-3\n" +
                "a7|-a6:a7:USUAL:a2#a6|-a7:a8:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-4\n" +
                "a8|-a7:a8:USUAL:a2#a6|-a8:a13:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-5\n" +
                "a13|-a2:a13:USUAL:a2#a13 a8:a13:USUAL:a2#a6|-a13:a13:USUAL:a2#a13|-EDGE_NODE|-a2#a13|-6\n" +
                "a13|-a13:a13:USUAL:a2#a13|-a13:a13:USUAL:a2#a13|-EDGE_NODE|-a2#a13|-7\n" +
                "a13|-a13:a13:USUAL:a2#a13|-a13:a14:USUAL:a2#a13|-COMMIT_NODE|-a2#a13|-8\n" +
                "a14|-a13:a14:USUAL:a2#a13|-a14:a15:USUAL:a2#a13|-COMMIT_NODE|-a2#a13|-9\n" +
                "a15|-a14:a15:USUAL:a2#a13|-|-COMMIT_NODE|-a2#a13|-10"
        );

    }

    @Test
    public void hardSeveralBranches() {
        Set<String> startNodes = new HashSet<String>();
        startNodes.add("a3");
        startNodes.add("a10");

        runTestGraphBranchesVisibility(
                hardGraph,

                startNodes,

                "a3|-|-a3:a4:USUAL:a3|-COMMIT_NODE|-a3|-0\n" +
                "a4|-a3:a4:USUAL:a3|-a4:a5:USUAL:a3|-COMMIT_NODE|-a3|-1\n" +
                "a5|-a4:a5:USUAL:a3|-a5:a6:USUAL:a3|-COMMIT_NODE|-a3|-2\n" +
                "a6|-a5:a6:USUAL:a3|-a6:a7:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-3\n" +
                "a7|-a6:a7:USUAL:a2#a6|-a7:a8:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-4\n" +
                "a8|-a7:a8:USUAL:a2#a6|-a8:a13:USUAL:a2#a6|-COMMIT_NODE|-a2#a6|-5\n" +
                "a13|-a8:a13:USUAL:a2#a6|-a13:a13:USUAL:a2#a13|-EDGE_NODE|-a2#a13|-6\n" +
                "a10|-|-a10:a11:USUAL:a10|-COMMIT_NODE|-a10|-7\n" +
                "   a13|-a13:a13:USUAL:a2#a13|-a13:a13:USUAL:a2#a13|-EDGE_NODE|-a2#a13|-7\n" +
                "a11|-a10:a11:USUAL:a10|-a11:a12:USUAL:a10|-COMMIT_NODE|-a10|-8\n" +
                "a12|-a11:a12:USUAL:a10|-a12:a13:USUAL:a10|-COMMIT_NODE|-a10|-9\n" +
                "a13|-a12:a13:USUAL:a10 a13:a13:USUAL:a2#a13|-a13:a14:USUAL:a2#a13|-COMMIT_NODE|-a2#a13|-10\n" +
                "a14|-a13:a14:USUAL:a2#a13|-a14:a15:USUAL:a2#a13|-COMMIT_NODE|-a2#a13|-11\n" +
                "a15|-a14:a15:USUAL:a2#a13|-|-COMMIT_NODE|-a2#a13|-12"
        );

    }

    @Test
    public void hardMiddleSelect() {
        Set<String> startNodes = new HashSet<String>();
        startNodes.add("a9");

        runTestGraphBranchesVisibility(
                hardGraph,

                startNodes,

                "a9|-|-a9:a13:USUAL:a9|-COMMIT_NODE|-a9|-0\n" +
                        "a13|-a9:a13:USUAL:a9|-a13:a13:USUAL:a2#a13|-EDGE_NODE|-a2#a13|-1\n" +
                        "a13|-a13:a13:USUAL:a2#a13|-a13:a14:USUAL:a2#a13|-COMMIT_NODE|-a2#a13|-2\n" +
                        "a14|-a13:a14:USUAL:a2#a13|-a14:a15:USUAL:a2#a13|-COMMIT_NODE|-a2#a13|-3\n" +
                        "a15|-a14:a15:USUAL:a2#a13|-|-COMMIT_NODE|-a2#a13|-4"
        );

    }

}
