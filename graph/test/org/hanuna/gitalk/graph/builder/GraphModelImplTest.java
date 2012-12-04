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
import static org.hanuna.gitalk.GraphTestUtils.*;

/**
 * @author erokhins
 */
public class GraphModelImplTest {

    private Graph builder(String inputTree) throws IOException {
        Branch.clearCountBranch();
        String input = inputTree.replace("\n", "|-aut|-132352112|-mes\n") + "|-aut|-132352112|-mes";
        GitLogParser parser = new GitLogParser(new StringReader(input));
        ReadOnlyList<Commit> commits = parser.readAllCommits();
        GraphBuilder builder = new GraphBuilder();
        return builder.build(commits);
    }

    @Test
    public void test1() throws IOException {
        Graph model = builder(
                "a0|-a3 a1 a5 a12\n" +
                "a1|-a2\n" +
                "a2|-a4\n" +
                "a3|-a5 a12\n" +
                "a4|-a7\n" +
                "a5|-a6\n" +
                "a6|-a8\n" +
                "a7|-a8\n" +
                "a8|-"
                );
        assertEquals(
                "a0|-|-a0:a3:USUAL:0 a0:a1:USUAL:1 a0:a5:USUAL:2 a0:a12:USUAL:3|-COMMIT_NODE|-0|-0\n" +
                "a1|-a0:a1:USUAL:1|-a1:a2:USUAL:1|-COMMIT_NODE|-1|-1\n" +
                "a2|-a1:a2:USUAL:1|-a2:a4:USUAL:1|-COMMIT_NODE|-1|-2\n" +
                "a3|-a0:a3:USUAL:0|-a3:a5:USUAL:0 a3:a12:USUAL:4|-COMMIT_NODE|-0|-3\n" +
                "a5|-a0:a5:USUAL:2 a3:a5:USUAL:0|-a5:a5:USUAL:2|-EDGE_NODE|-2|-4\n" +
                "   a12|-a0:a12:USUAL:3 a3:a12:USUAL:4|-a12:a12:USUAL:3|-EDGE_NODE|-3|-4\n" +
                "   a4|-a2:a4:USUAL:1|-a4:a7:USUAL:1|-COMMIT_NODE|-1|-4\n" +
                "a5|-a5:a5:USUAL:2|-a5:a6:USUAL:2|-COMMIT_NODE|-2|-5\n" +
                "a6|-a5:a6:USUAL:2|-a6:a8:USUAL:2|-COMMIT_NODE|-2|-6\n" +
                "a7|-a4:a7:USUAL:1|-a7:a8:USUAL:1|-COMMIT_NODE|-1|-7\n" +
                "a8|-a6:a8:USUAL:2 a7:a8:USUAL:1|-|-COMMIT_NODE|-1|-8\n" +
                "a12|-a12:a12:USUAL:3|-|-END_COMMIT_NODE|-3|-9"
                ,
                toStr(model)
        );

        model.hideBranch(getNode(model, 1), getNode(model, 7));

        assertEquals(
                "a0|-|-a0:a3:USUAL:0 a0:a1:USUAL:1 a0:a5:USUAL:2 a0:a12:USUAL:3|-COMMIT_NODE|-0|-0\n" +
                "a1|-a0:a1:USUAL:1|-a1:a7:HIDE_BRANCH:1|-COMMIT_NODE|-1|-1\n" +
                "a3|-a0:a3:USUAL:0|-a3:a5:USUAL:0 a3:a12:USUAL:4|-COMMIT_NODE|-0|-2\n" +
                "a5|-a0:a5:USUAL:2 a3:a5:USUAL:0|-a5:a5:USUAL:2|-EDGE_NODE|-2|-3\n" +
                "   a12|-a0:a12:USUAL:3 a3:a12:USUAL:4|-a12:a12:USUAL:3|-EDGE_NODE|-3|-3\n" +
                "a5|-a5:a5:USUAL:2|-a5:a6:USUAL:2|-COMMIT_NODE|-2|-4\n" +
                "a6|-a5:a6:USUAL:2|-a6:a8:USUAL:2|-COMMIT_NODE|-2|-5\n" +
                "a7|-a1:a7:HIDE_BRANCH:1|-a7:a8:USUAL:1|-COMMIT_NODE|-1|-6\n" +
                "a8|-a6:a8:USUAL:2 a7:a8:USUAL:1|-|-COMMIT_NODE|-1|-7\n" +
                "a12|-a12:a12:USUAL:3|-|-END_COMMIT_NODE|-3|-8"
                ,
                toStr(model)
        );


        model.showBranch(getNode(model, 1).getDownEdges().get(0));

        assertEquals(
                "a0|-|-a0:a3:USUAL:0 a0:a1:USUAL:1 a0:a5:USUAL:2 a0:a12:USUAL:3|-COMMIT_NODE|-0|-0\n" +
                "a1|-a0:a1:USUAL:1|-a1:a2:USUAL:1|-COMMIT_NODE|-1|-1\n" +
                "a2|-a1:a2:USUAL:1|-a2:a4:USUAL:1|-COMMIT_NODE|-1|-2\n" +
                "a3|-a0:a3:USUAL:0|-a3:a5:USUAL:0 a3:a12:USUAL:4|-COMMIT_NODE|-0|-3\n" +
                "a5|-a0:a5:USUAL:2 a3:a5:USUAL:0|-a5:a5:USUAL:2|-EDGE_NODE|-2|-4\n" +
                "   a12|-a0:a12:USUAL:3 a3:a12:USUAL:4|-a12:a12:USUAL:3|-EDGE_NODE|-3|-4\n" +
                "   a4|-a2:a4:USUAL:1|-a4:a7:USUAL:1|-COMMIT_NODE|-1|-4\n" +
                "a5|-a5:a5:USUAL:2|-a5:a6:USUAL:2|-COMMIT_NODE|-2|-5\n" +
                "a6|-a5:a6:USUAL:2|-a6:a8:USUAL:2|-COMMIT_NODE|-2|-6\n" +
                "a7|-a4:a7:USUAL:1|-a7:a8:USUAL:1|-COMMIT_NODE|-1|-7\n" +
                "a8|-a6:a8:USUAL:2 a7:a8:USUAL:1|-|-COMMIT_NODE|-1|-8\n" +
                "a12|-a12:a12:USUAL:3|-|-END_COMMIT_NODE|-3|-9"
                ,
                toStr(model)
        );
    }

}
