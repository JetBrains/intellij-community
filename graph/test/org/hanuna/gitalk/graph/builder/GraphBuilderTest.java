package org.hanuna.gitalk.graph.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.Branch;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.parser.GitLogParser;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

import static junit.framework.Assert.assertEquals;
import static org.hanuna.gitalk.GraphTestUtils.toStr;

/**
 * @author erokhins
 */
public class GraphBuilderTest {

    public void runTest(String inputTree, String out) throws IOException {
        Branch.clearCountBranch();
        String input = inputTree.replace("\n", "|-aut|-132352112|-mes\n") + "|-aut|-132352112|-mes";
        GitLogParser parser = new GitLogParser(new StringReader(input));
        ReadOnlyList<Commit> commits = parser.readAllCommits();
        GraphBuilder builder = new GraphBuilder();
        Graph model = builder.build(commits);
       // System.out.println(toStr(model));
        assertEquals(out, toStr(model));

    }

    @Test
    public void simple1() throws IOException {
        runTest("12|-", "12|-|-|-COMMIT_NODE|-0|-0");
    }

    @Test
    public void simple2() throws IOException {
        runTest(
                "12|-af\n" +
                "af|-",

                "12|-|-12:af:USUAL:0|-COMMIT_NODE|-0|-0\n" +
                "af|-12:af:USUAL:0|-|-COMMIT_NODE|-0|-1"
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

                "a0|-|-a0:a3:USUAL:0 a0:a1:USUAL:1|-COMMIT_NODE|-0|-0\n" +
                "a1|-a0:a1:USUAL:1|-a1:a2:USUAL:1 a1:a4:USUAL:2|-COMMIT_NODE|-1|-1\n" +
                "a2|-a1:a2:USUAL:1|-a2:a3:USUAL:1 a2:a5:USUAL:3 a2:a8:USUAL:4|-COMMIT_NODE|-1|-2\n" +
                "a3|-a0:a3:USUAL:0 a2:a3:USUAL:1|-a3:a6:USUAL:0|-COMMIT_NODE|-0|-3\n" +
                "a4|-a1:a4:USUAL:2|-a4:a7:USUAL:2|-COMMIT_NODE|-2|-4\n" +
                "a5|-a2:a5:USUAL:3|-a5:a7:USUAL:3|-COMMIT_NODE|-3|-5\n" +
                "a7|-a4:a7:USUAL:2 a5:a7:USUAL:3|-a7:a7:USUAL:2|-EDGE_NODE|-2|-6\n" +
                "   a6|-a3:a6:USUAL:0|-a6:a7:USUAL:0|-COMMIT_NODE|-0|-6\n" +
                "a7|-a7:a7:USUAL:2 a6:a7:USUAL:0|-|-COMMIT_NODE|-0|-7\n" +
                "a8|-a2:a8:USUAL:4|-|-COMMIT_NODE|-4|-8"
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

                "a0|-|-a0:a5:USUAL:0 a0:a1:USUAL:1 a0:a3:USUAL:2|-COMMIT_NODE|-0|-0\n" +
                "a1|-a0:a1:USUAL:1|-a1:a4:USUAL:1|-COMMIT_NODE|-1|-1\n" +
                "a2|-|-a2:a6:USUAL:3|-COMMIT_NODE|-3|-2\n" +
                "a3|-a0:a3:USUAL:2|-a3:a6:USUAL:2|-COMMIT_NODE|-2|-3\n" +
                "a6|-a2:a6:USUAL:3 a3:a6:USUAL:2|-a6:a6:USUAL:3|-EDGE_NODE|-3|-4\n" +
                "   a4|-a1:a4:USUAL:1|-a4:a6:USUAL:1|-COMMIT_NODE|-1|-4\n" +
                "a6|-a6:a6:USUAL:3 a4:a6:USUAL:1|-a6:a6:USUAL:3|-EDGE_NODE|-3|-5\n" +
                "   a5|-a0:a5:USUAL:0|-a5:a6:USUAL:0|-COMMIT_NODE|-0|-5\n" +
                "a6|-a6:a6:USUAL:3 a5:a6:USUAL:0|-a6:a7:USUAL:0|-COMMIT_NODE|-0|-6\n" +
                "a7|-a6:a7:USUAL:0|-a7:a8:USUAL:0|-COMMIT_NODE|-0|-7\n" +
                "a8|-a7:a8:USUAL:0|-|-COMMIT_NODE|-0|-8"
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
                "1|-|-1:2:USUAL:0 1:3:USUAL:1 1:4:USUAL:2|-COMMIT_NODE|-0|-0\n" +
                "2|-1:2:USUAL:0|-2:5:USUAL:0|-COMMIT_NODE|-0|-1\n" +
                "3|-1:3:USUAL:1|-3:8:USUAL:1|-COMMIT_NODE|-1|-2\n" +
                "4|-1:4:USUAL:2|-4:6:USUAL:2 4:8:USUAL:3|-COMMIT_NODE|-2|-3\n" +
                "8|-3:8:USUAL:1 4:8:USUAL:3|-8:8:USUAL:1|-EDGE_NODE|-1|-4\n" +
                "   5|-2:5:USUAL:0|-5:8:USUAL:0|-COMMIT_NODE|-0|-4\n" +
                "8|-8:8:USUAL:1 5:8:USUAL:0|-8:8:USUAL:1|-EDGE_NODE|-1|-5\n" +
                "   6|-4:6:USUAL:2|-6:8:USUAL:2 6:7:USUAL:4|-COMMIT_NODE|-2|-5\n" +
                "8|-8:8:USUAL:1 6:8:USUAL:2|-8:8:USUAL:1|-EDGE_NODE|-1|-6\n" +
                "   7|-6:7:USUAL:4|-7:8:USUAL:4|-COMMIT_NODE|-4|-6\n" +
                "8|-8:8:USUAL:1 7:8:USUAL:4|-|-COMMIT_NODE|-1|-7"
                 );
    }

}
