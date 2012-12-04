package org.hanuna.gitalk.printmodel.cells.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.Branch;
import org.hanuna.gitalk.graph.builder.GraphBuilder;
import org.hanuna.gitalk.parser.GitLogParser;
import org.hanuna.gitalk.printmodel.cells.LayoutModel;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

import static junit.framework.Assert.assertEquals;
import static org.hanuna.gitalk.printmodel.LayoutTestUtils.toStr;

/**
 * @author erokhins
 */
public class LayoutModelBuilderTest {
    private void runTest(String inputTree, String out) throws IOException {
        Branch.clearCountBranch();
        String input = inputTree.replace("\n", "|-aut|-132352112|-mes\n") + "|-aut|-132352112|-mes";
        GitLogParser parser = new GitLogParser(new StringReader(input));
        ReadOnlyList<Commit> commits = parser.readAllCommits();
        GraphBuilder builder = new GraphBuilder();
        LayoutModelBuilder builder1 = new LayoutModelBuilder(builder.build(commits));
        LayoutModel layoutModel = builder1.build();
        assertEquals(out, toStr(layoutModel));
    }

    @Test
    public void test1() throws IOException {
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

            "a0\n" +
            "a0:a3 a1\n" +
            "a0:a3 a2 a1:a4\n" +
            "a3 a2:a5 a2:a8 a1:a4\n" +
            "a3:a6 a2:a5 a2:a8 a4\n" +
            "a3:a6 a5 a2:a8 a4:a7\n" +
            "a6 a2:a8 a7\n" +
            "a2:a8 a7\n" +
            "a8"
        );

    }


}
