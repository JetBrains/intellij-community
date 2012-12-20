package org.hanuna.gitalk.parser;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

import static junit.framework.Assert.assertEquals;
import static org.hanuna.gitalk.CommitTestUtils.toShortStr;

/**
 * @author erokhins
 */
public class GitLogParserTests {

    private void runLogParserTest(String in, boolean full) throws IOException {
        String input = in.replace("\n", "|-aut|-132352112|-mes\n") + "|-aut|-132352112|-mes";
        GitLogParser parser = new GitLogParser(new StringReader(input));
        ReadOnlyList<Commit> commits = parser.readAllCommits();
        assertEquals(in, toShortStr(commits));
        assertEquals(full, parser.allCommitsFound());
    }

    private void runLogParserTest(String in) throws IOException {
        runLogParserTest(in, true);
    }

    @Test
    public void simple() throws IOException {
        runLogParserTest(
                "12|-"
        );
    }

    @Test
    public void twoLine() throws IOException {
        runLogParserTest(
                "12|-af\n" +
                "af|-"

        );
    }

    @Test
    public void testLogParser() throws IOException {
        runLogParserTest(
                "a0|-a3 a1\n" +
                "a1|-a2 a4\n" +
                "a2|-a3 a5 a8\n" +
                "a3|-a6\n" +
                "a4|-a7\n" +
                "a5|-a7\n" +
                "a6|-a7\n" +
                "a7|-\n" +
                "a8|-"

        );
    }

    @Test
    public void notFullLogTest() throws IOException {
        runLogParserTest(
                "a0|-a3 a1\n" +
                "a1|-a2 a4\n" +
                "a2|-a3 a5 a8\n" +
                "a3|-a6\n" +
                "a4|-a7\n" +
                "a5|-a7\n" +
                "a6|-a7",

                false
        );
    }

}
