package org.hanuna.gitalk.graph.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.graph.Branch;
import org.hanuna.gitalk.graph.GraphModel;
import org.hanuna.gitalk.parser.GitLogParser;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

import static junit.framework.Assert.assertEquals;
import static org.hanuna.gitalk.TestUtils.toStr;

/**
 * @author erokhins
 */
public class GraphModelBuilderTest {

    public void runTest(String inputTree, String out) throws IOException {
        Branch.clearCountBranch();
        String input = inputTree.replace("\n", "|-aut|-132352112|-mes\n") + "|-aut|-132352112|-mes";
        GitLogParser parser = new GitLogParser(new StringReader(input));
        ReadOnlyList<Commit> commits = parser.getFirstPart();
        GraphModelBuilder builder = new GraphModelBuilder();
        GraphModel model = builder.build(commits);
       // System.out.println(toStr(model));
        assertEquals(toStr(model), out);

    }

    @Test
    public void simple1() throws IOException {
        runTest("12|-", "12|-|-|-commitNode|-0|-0");
    }

    @Test
    public void simple2() throws IOException {
        runTest(
                "12|-af\n" +
                "af|-",

                "12|-|-12:af:usual:0|-commitNode|-0|-0\n" +
                "af|-12:af:usual:0|-|-commitNode|-0|-1"
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

                "a0|-|-a0:a3:usual:0 a0:a1:usual:1|-commitNode|-0|-0\n" +
                "a1|-a0:a1:usual:1|-a1:a2:usual:1 a1:a4:usual:2|-commitNode|-1|-1\n" +
                "a2|-a1:a2:usual:1|-a2:a3:usual:1 a2:a5:usual:3 a2:a8:usual:4|-commitNode|-1|-2\n" +
                "a3|-a0:a3:usual:0 a2:a3:usual:1|-a3:a6:usual:0|-commitNode|-0|-3\n" +
                "a4|-a1:a4:usual:2|-a4:a7:usual:2|-commitNode|-2|-4\n" +
                "a5|-a2:a5:usual:3|-a5:a7:usual:3|-commitNode|-3|-5\n" +
                "a7|-a4:a7:usual:2 a5:a7:usual:3|-a7:a7:usual:2|-edgeNode|-2|-6\n" +
                "   a6|-a3:a6:usual:0|-a6:a7:usual:0|-commitNode|-0|-6\n" +
                "a7|-a7:a7:usual:2 a6:a7:usual:0|-|-commitNode|-2|-7\n" +
                "a8|-a2:a8:usual:4|-|-commitNode|-4|-8"
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
                "1|-|-1:2:usual:0 1:3:usual:1 1:4:usual:2|-commitNode|-0|-0\n" +
                "2|-1:2:usual:0|-2:5:usual:0|-commitNode|-0|-1\n" +
                "3|-1:3:usual:1|-3:8:usual:1|-commitNode|-1|-2\n" +
                "4|-1:4:usual:2|-4:6:usual:2 4:8:usual:3|-commitNode|-2|-3\n" +
                "8|-3:8:usual:1 4:8:usual:3|-8:8:usual:1|-edgeNode|-1|-4\n" +
                "   5|-2:5:usual:0|-5:8:usual:0|-commitNode|-0|-4\n" +
                "8|-8:8:usual:1 5:8:usual:0|-8:8:usual:1|-edgeNode|-1|-5\n" +
                "   6|-4:6:usual:2|-6:8:usual:2 6:7:usual:4|-commitNode|-2|-5\n" +
                "8|-8:8:usual:1 6:8:usual:2|-8:8:usual:1|-edgeNode|-1|-6\n" +
                "   7|-6:7:usual:4|-7:8:usual:4|-commitNode|-4|-6\n" +
                "8|-8:8:usual:1 7:8:usual:4|-|-commitNode|-1|-7"
                 );
    }

}
