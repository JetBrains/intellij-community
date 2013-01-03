package org.hanuna.gitalk.graph.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.mutable.GraphBuilder;
import org.hanuna.gitalk.parser.SimpleCommitListParser;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static org.hanuna.gitalk.GraphTestUtils.toStr;

/**
 * @author erokhins
 */
public class GraphBuilderTest {

    public void runTest(String input, String out) throws IOException {
        SimpleCommitListParser parser = new SimpleCommitListParser(new StringReader(input));
        List<Commit> commits = parser.readAllCommits();
        Graph model = GraphBuilder.build(commits);
        assertEquals(out, toStr(model));

    }

    @Test
    public void simple1() throws IOException {
        runTest("12|-", "12|-|-|-COMMIT_NODE|-851|-0");
    }

    @Test
    public void simple2() throws IOException {
        runTest(
                "12|-af\n" +
                "af|-",

                "12|-|-12:af:USUAL:851|-COMMIT_NODE|-851|-0\n" +
                "af|-12:af:USUAL:851|-|-COMMIT_NODE|-851|-1"
        );
    }

    @Test
    public void moreBranches() throws IOException {
        runTest(
                "a0|-a3 a1\n" +
                "a1|-a2 a4\n" +
                "a2|-a3 a5 a8\n" +
                "a3|-a6\n" +
                "a4|-a7\n" +
                "a5|-a7\n" +
                "a6|-a7\n" +
                "a7|-\n" +
                "a8|-",

                "a0|-|-a0:a3:USUAL:993 a0:a1:USUAL:994|-COMMIT_NODE|-993|-0\n" +
                "a1|-a0:a1:USUAL:994|-a1:a2:USUAL:994 a1:a4:USUAL:997|-COMMIT_NODE|-994|-1\n" +
                "a2|-a1:a2:USUAL:994|-a2:a3:USUAL:994 a2:a5:USUAL:998 a2:a8:USUAL:1001|-COMMIT_NODE|-994|-2\n" +
                "a3|-a0:a3:USUAL:993 a2:a3:USUAL:994|-a3:a6:USUAL:993|-COMMIT_NODE|-993|-3\n" +
                "a4|-a1:a4:USUAL:997|-a4:a7:USUAL:997|-COMMIT_NODE|-997|-4\n" +
                "a5|-a2:a5:USUAL:998|-a5:a7:USUAL:998|-COMMIT_NODE|-998|-5\n" +
                "a7|-a4:a7:USUAL:997 a5:a7:USUAL:998|-a7:a7:USUAL:997|-EDGE_NODE|-997|-6\n" +
                "   a6|-a3:a6:USUAL:993|-a6:a7:USUAL:993|-COMMIT_NODE|-993|-6\n" +
                "a7|-a7:a7:USUAL:997 a6:a7:USUAL:993|-|-COMMIT_NODE|-997|-7\n" +
                "a8|-a2:a8:USUAL:1001|-|-COMMIT_NODE|-1001|-8"
        );
    }

    @Test
    public void youngerBranches() throws IOException {
        runTest(
                "a0|-a5 a1 a3\n" +
                "a1|-a4\n" +
                "a2|-a6\n" +
                "a3|-a6\n" +
                "a4|-a6\n" +
                "a5|-a6\n" +
                "a6|-a7\n" +
                "a7|-a8\n" +
                "a8|-",

                "a0|-|-a0:a5:USUAL:993 a0:a1:USUAL:994 a0:a3:USUAL:996|-COMMIT_NODE|-993|-0\n" +
                "a1|-a0:a1:USUAL:994|-a1:a4:USUAL:994|-COMMIT_NODE|-994|-1\n" +
                "a2|-|-a2:a6:USUAL:995|-COMMIT_NODE|-995|-2\n" +
                "a3|-a0:a3:USUAL:996|-a3:a6:USUAL:996|-COMMIT_NODE|-996|-3\n" +
                "a6|-a2:a6:USUAL:995 a3:a6:USUAL:996|-a6:a6:USUAL:995|-EDGE_NODE|-995|-4\n" +
                "   a4|-a1:a4:USUAL:994|-a4:a6:USUAL:994|-COMMIT_NODE|-994|-4\n" +
                "a6|-a6:a6:USUAL:995 a4:a6:USUAL:994|-a6:a6:USUAL:995|-EDGE_NODE|-995|-5\n" +
                "   a5|-a0:a5:USUAL:993|-a5:a6:USUAL:993|-COMMIT_NODE|-993|-5\n" +
                "a6|-a6:a6:USUAL:995 a5:a6:USUAL:993|-a6:a7:USUAL:995|-COMMIT_NODE|-995|-6\n" +
                "a7|-a6:a7:USUAL:995|-a7:a8:USUAL:995|-COMMIT_NODE|-995|-7\n" +
                "a8|-a7:a8:USUAL:995|-|-COMMIT_NODE|-995|-8"
        );
    }

    @Test
    public void moreEdgesNodes() throws IOException {
        runTest(
                "1|-2 3 4\n" +
                "2|-5\n" +
                "3|-8\n" +
                "4|-6 8\n" +
                "5|-8\n" +
                "6|-8 7\n" +
                "7|-8\n" +
                "8|-"
                ,
                "1|-|-1:2:USUAL:865 1:3:USUAL:867 1:4:USUAL:868|-COMMIT_NODE|-865|-0\n" +
                "2|-1:2:USUAL:865|-2:5:USUAL:865|-COMMIT_NODE|-865|-1\n" +
                "3|-1:3:USUAL:867|-3:8:USUAL:867|-COMMIT_NODE|-867|-2\n" +
                "4|-1:4:USUAL:868|-4:6:USUAL:868 4:8:USUAL:872|-COMMIT_NODE|-868|-3\n" +
                "8|-3:8:USUAL:867 4:8:USUAL:872|-8:8:USUAL:867|-EDGE_NODE|-867|-4\n" +
                "   5|-2:5:USUAL:865|-5:8:USUAL:865|-COMMIT_NODE|-865|-4\n" +
                "8|-8:8:USUAL:867 5:8:USUAL:865|-8:8:USUAL:867|-EDGE_NODE|-867|-5\n" +
                "   6|-4:6:USUAL:868|-6:8:USUAL:868 6:7:USUAL:871|-COMMIT_NODE|-868|-5\n" +
                "8|-8:8:USUAL:867 6:8:USUAL:868|-8:8:USUAL:867|-EDGE_NODE|-867|-6\n" +
                "   7|-6:7:USUAL:871|-7:8:USUAL:871|-COMMIT_NODE|-871|-6\n" +
                "8|-8:8:USUAL:867 7:8:USUAL:871|-|-COMMIT_NODE|-867|-7"
                 );
    }

    @Test
    public void notFullLog() throws IOException  {
        runTest(
                "1|-2 3 4\n" +
                "2|-5\n" +
                "3|-8\n" +
                "4|-6 8\n" +
                "5|-8\n" +
                "6|-8 7"
                ,
                "1|-|-1:2:USUAL:865 1:3:USUAL:867 1:4:USUAL:868|-COMMIT_NODE|-865|-0\n" +
                "2|-1:2:USUAL:865|-2:5:USUAL:865|-COMMIT_NODE|-865|-1\n" +
                "3|-1:3:USUAL:867|-3:8:USUAL:867|-COMMIT_NODE|-867|-2\n" +
                "4|-1:4:USUAL:868|-4:6:USUAL:868 4:8:USUAL:872|-COMMIT_NODE|-868|-3\n" +
                "8|-3:8:USUAL:867 4:8:USUAL:872|-8:8:USUAL:867|-EDGE_NODE|-867|-4\n" +
                "   5|-2:5:USUAL:865|-5:8:USUAL:865|-COMMIT_NODE|-865|-4\n" +
                "8|-8:8:USUAL:867 5:8:USUAL:865|-8:8:USUAL:867|-EDGE_NODE|-867|-5\n" +
                "   6|-4:6:USUAL:868|-6:8:USUAL:868 6:7:USUAL:871|-COMMIT_NODE|-868|-5\n" +
                "7|-6:7:USUAL:871|-|-END_COMMIT_NODE|-871|-6\n" +
                "   8|-8:8:USUAL:867 6:8:USUAL:868|-|-END_COMMIT_NODE|-867|-6"
        );
    }

}
