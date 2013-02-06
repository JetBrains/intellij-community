package org.hanuna.gitalk.graph.new_mutable;

import org.hanuna.gitalk.log.commit.Commit;
import org.hanuna.gitalk.graph.mutable.GraphBuilder;
import org.hanuna.gitalk.graph.mutable.MutableGraph;
import org.hanuna.gitalk.log.parser.SimpleCommitListParser;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static org.hanuna.gitalk.GraphStrUtils.toStr;

/**
 * @author erokhins
 */
public class NewGraphBuilderTest {

    public void runTest(String input, String out) {
        SimpleCommitListParser parser = new SimpleCommitListParser(new StringReader(input));
        List<Commit> commits;
        try {
            commits = parser.readAllCommits();
        } catch (IOException e) {
            throw new IllegalStateException();
        }
        MutableGraph graph = GraphBuilder.build(commits);
        assertEquals(out, toStr(graph));
    }



    @Test
    public void simple1() {
        runTest("12|-", "12|-|-|-COMMIT_NODE|-12|-0");
    }

    @Test
    public void simple2() {
        runTest(
                "12|-af\n" +
                "af|-",

                "12|-|-12:af:USUAL:12|-COMMIT_NODE|-12|-0\n" +
                        "af|-12:af:USUAL:12|-|-COMMIT_NODE|-12|-1"
        );
    }

    @Test
    public void simple3() {
        runTest(
                "a0|-a1\n" +
                "a1|-a2\n"  +
                "a2|-",

                "a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a0|-a1:a2:USUAL:a0|-COMMIT_NODE|-a0|-1\n" +
                "a2|-a1:a2:USUAL:a0|-|-COMMIT_NODE|-a0|-2"
        );
    }

    @Test
    public void moreParents() {
        runTest(
                "a0|-a1 a2 a3\n" +
                "a1|-\n" +
                "a2|-\n" +
                "a3|-",

                "a0|-|-a0:a1:USUAL:a0#a1 a0:a2:USUAL:a0#a2 a0:a3:USUAL:a0#a3|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a0#a1|-|-COMMIT_NODE|-a0#a1|-1\n" +
                "a2|-a0:a2:USUAL:a0#a2|-|-COMMIT_NODE|-a0#a2|-2\n" +
                "a3|-a0:a3:USUAL:a0#a3|-|-COMMIT_NODE|-a0#a3|-3"
        );
    }

    @Test
    public void edgeNodes() {
        runTest(
                "a0|-a1 a3\n" +
                "a1|-a3 a2\n" +
                "a2|-\n" +
                "a3|-"
                ,

                "a0|-|-a0:a1:USUAL:a0#a1 a0:a3:USUAL:a0#a3|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a0#a1|-a1:a2:USUAL:a1#a2 a1:a3:USUAL:a1#a3|-COMMIT_NODE|-a0#a1|-1\n" +
                "a3|-a0:a3:USUAL:a0#a3 a1:a3:USUAL:a1#a3|-a3:a3:USUAL:a0#a3|-EDGE_NODE|-a0#a3|-2\n" +
                "   a2|-a1:a2:USUAL:a1#a2|-|-COMMIT_NODE|-a1#a2|-2\n" +
                "a3|-a3:a3:USUAL:a0#a3|-|-COMMIT_NODE|-a0#a3|-3"
        );
    }
    
    @Test
    public void nodeEdge2() {
        runTest(
                "a0|-a1 a3\n" +
                "a1|-a3\n" +
                "a2|-\n" +
                "a3|-",
                
                "a0|-|-a0:a1:USUAL:a0#a1 a0:a3:USUAL:a0#a3|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a0#a1|-a1:a3:USUAL:a0#a1|-COMMIT_NODE|-a0#a1|-1\n" +
                "a3|-a0:a3:USUAL:a0#a3 a1:a3:USUAL:a0#a1|-a3:a3:USUAL:a0#a3|-EDGE_NODE|-a0#a3|-2\n" +
                "   a2|-|-|-COMMIT_NODE|-a2|-2\n" +
                "a3|-a3:a3:USUAL:a0#a3|-|-COMMIT_NODE|-a0#a3|-3"
        );
    }

    @Test
    public void twoChildren() {
        runTest(
                "a0|-a1 a2\n" +
                "a1|-a2\n" +
                "a2|-",
                
                "a0|-|-a0:a1:USUAL:a0#a1 a0:a2:USUAL:a0#a2|-COMMIT_NODE|-a0|-0\n" +
                "a1|-a0:a1:USUAL:a0#a1|-a1:a2:USUAL:a0#a1|-COMMIT_NODE|-a0#a1|-1\n" +
                "a2|-a0:a2:USUAL:a0#a2 a1:a2:USUAL:a0#a1|-|-COMMIT_NODE|-a0#a2|-2"
        );
    }
}
