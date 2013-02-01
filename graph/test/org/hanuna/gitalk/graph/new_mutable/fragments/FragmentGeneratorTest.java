package org.hanuna.gitalk.graph.new_mutable.fragments;

import org.hanuna.gitalk.GraphTestUtils;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.new_mutable.MutableGraph;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static org.hanuna.gitalk.GraphStrUtils.toStr;
import static org.hanuna.gitalk.GraphTestUtils.getCommitNode;

/**
 * @author erokhins
 */
public class FragmentGeneratorTest {

    public void runTest(@NotNull String inputGraph, int rowIndex, String fragmentStr) {
        MutableGraph graph = GraphTestUtils.getNewMutableGraph(inputGraph);
        FragmentGenerator fragmentGenerator = new FragmentGenerator(graph);
        Node commitNode = getCommitNode(graph, rowIndex);
        NewGraphFragment fragment = fragmentGenerator.getShortSmallFragment(commitNode);
        assertEquals(fragmentStr, toStr(fragment));
    }



    @Test
    public void simpleTest() {
        runTest(
                "a0|-a1\n" +
                "a1|-a2\n"  +
                "a2|-",
                0,

                "a0:0|-|-a1:1"
                );
    }

    @Test
    public void simpleNull() {
        runTest(
                "a0|-a1\n" +
                "a1|-a2\n"  +
                "a2|-",
                2,
                "null"
                );
    }


    @Test
    public void severalChildren() {
        runTest(
                "a0|-a1 a2\n" +
                "a1|-a2\n"  +
                "a2|-",
                0,
                "a0:0|-a1:1|-a2:2"
        );
    }


    @Test
    public void badEndNode() {
        runTest(
                "a0|-a1 a3\n" +
                "a1|-a3\n"  +
                "a2|-a3\n" +
                "a3|-",
                0,
                "null"
        );
    }


    @Test
    public void badIntermediateNode() {
        runTest(
                "a0|-a2 a3\n" +
                "a1|-a2\n"  +
                "a2|-a3\n" +
                "a3|-",
                0,
                "null"
        );
    }

    @Test
    public void longEndNode() {
        runTest(
                "a0|-a1 a2\n" +
                "a1|-a2\n"  +
                "a2|-a3\n" +
                "a3|-",
                0,
                "a0:0|-a1:1|-a2:2"
        );
    }


    @Test
    public void edgeNodes() {
        runTest(
                "a0|-a3 a1\n" +
                "a1|-a2 a3\n"  +
                "a2|-a3\n" +
                "a3|-",
                0,
                "a0:0|-a1:1 a2:2 a3:2|-a3:3"
        );
    }

}
