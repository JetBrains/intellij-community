package org.hanuna.gitalk.graph.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.Branch;
import org.hanuna.gitalk.graph.GraphModel;
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

    private GraphModel builder(String inputTree) throws IOException {
        Branch.clearCountBranch();
        String input = inputTree.replace("\n", "|-aut|-132352112|-mes\n") + "|-aut|-132352112|-mes";
        GitLogParser parser = new GitLogParser(new StringReader(input));
        ReadOnlyList<Commit> commits = parser.readAllCommits();
        GraphModelBuilder builder = new GraphModelBuilder();
        return builder.build(commits);
    }

    @Test
    public void test1() throws IOException {
        GraphModel model = builder(
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
                "a0|-|-a0:a3:usual:0 a0:a1:usual:1 a0:a5:usual:2 a0:a12:usual:3|-commitNode|-0|-0\n" +
                "a1|-a0:a1:usual:1|-a1:a2:usual:1|-commitNode|-1|-1\n" +
                "a2|-a1:a2:usual:1|-a2:a4:usual:1|-commitNode|-1|-2\n" +
                "a3|-a0:a3:usual:0|-a3:a5:usual:0 a3:a12:usual:4|-commitNode|-0|-3\n" +
                "a5|-a0:a5:usual:2 a3:a5:usual:0|-a5:a5:usual:2|-edgeNode|-2|-4\n" +
                "   a12|-a0:a12:usual:3 a3:a12:usual:4|-a12:a12:usual:3|-edgeNode|-3|-4\n" +
                "   a4|-a2:a4:usual:1|-a4:a7:usual:1|-commitNode|-1|-4\n" +
                "a5|-a5:a5:usual:2|-a5:a6:usual:2|-commitNode|-2|-5\n" +
                "a6|-a5:a6:usual:2|-a6:a8:usual:2|-commitNode|-2|-6\n" +
                "a7|-a4:a7:usual:1|-a7:a8:usual:1|-commitNode|-1|-7\n" +
                "a8|-a6:a8:usual:2 a7:a8:usual:1|-|-commitNode|-2|-8\n" +
                "a12|-a12:a12:usual:3|-|-endCommitNode|-3|-9"
                ,
                toStr(model)
        );

        model.hideBranch(getNode(model, 1), getNode(model, 7));

        assertEquals(
                "a0|-|-a0:a3:usual:0 a0:a1:usual:1 a0:a5:usual:2 a0:a12:usual:3|-commitNode|-0|-0\n" +
                "a1|-a0:a1:usual:1|-a1:a7:hideBranch:1|-commitNode|-1|-1\n" +
                "a3|-a0:a3:usual:0|-a3:a5:usual:0 a3:a12:usual:4|-commitNode|-0|-2\n" +
                "a5|-a0:a5:usual:2 a3:a5:usual:0|-a5:a5:usual:2|-edgeNode|-2|-3\n" +
                "   a12|-a0:a12:usual:3 a3:a12:usual:4|-a12:a12:usual:3|-edgeNode|-3|-3\n" +
                "a5|-a5:a5:usual:2|-a5:a6:usual:2|-commitNode|-2|-4\n" +
                "a6|-a5:a6:usual:2|-a6:a8:usual:2|-commitNode|-2|-5\n" +
                "a7|-a1:a7:hideBranch:1|-a7:a8:usual:1|-commitNode|-1|-6\n" +
                "a8|-a6:a8:usual:2 a7:a8:usual:1|-|-commitNode|-2|-7\n" +
                "a12|-a12:a12:usual:3|-|-endCommitNode|-3|-8"
                ,
                toStr(model)
        );


        model.showBranch(getNode(model, 1).getDownEdges().get(0));

        assertEquals(
                "a0|-|-a0:a3:usual:0 a0:a1:usual:1 a0:a5:usual:2 a0:a12:usual:3|-commitNode|-0|-0\n" +
                "a1|-a0:a1:usual:1|-a1:a2:usual:1|-commitNode|-1|-1\n" +
                "a2|-a1:a2:usual:1|-a2:a4:usual:1|-commitNode|-1|-2\n" +
                "a3|-a0:a3:usual:0|-a3:a5:usual:0 a3:a12:usual:4|-commitNode|-0|-3\n" +
                "a5|-a0:a5:usual:2 a3:a5:usual:0|-a5:a5:usual:2|-edgeNode|-2|-4\n" +
                "   a12|-a0:a12:usual:3 a3:a12:usual:4|-a12:a12:usual:3|-edgeNode|-3|-4\n" +
                "   a4|-a2:a4:usual:1|-a4:a7:usual:1|-commitNode|-1|-4\n" +
                "a5|-a5:a5:usual:2|-a5:a6:usual:2|-commitNode|-2|-5\n" +
                "a6|-a5:a6:usual:2|-a6:a8:usual:2|-commitNode|-2|-6\n" +
                "a7|-a4:a7:usual:1|-a7:a8:usual:1|-commitNode|-1|-7\n" +
                "a8|-a6:a8:usual:2 a7:a8:usual:1|-|-commitNode|-2|-8\n" +
                "a12|-a12:a12:usual:3|-|-endCommitNode|-3|-9"
                ,
                toStr(model)
        );
    }

}
