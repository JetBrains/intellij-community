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

    private void runLogParserTest(String in, String out, boolean full) throws IOException {
        String input = in.replace("\n", "|-aut|-132352112|-mes\n") + "|-aut|-132352112|-mes";
        GitLogParser parser = new GitLogParser(new StringReader(input));
        ReadOnlyList<Commit> commits = parser.readAllCommits();
        assertEquals(out, toShortStr(commits));
        assertEquals(full, parser.allCommitsFound());
    }

    private void runLogParserTest(String in, String out) throws IOException {
        runLogParserTest(in, out, true);
    }

    @Test
    public void simple() throws IOException {
        runLogParserTest(
                "12|-",
                "0|-|-12"
        );
    }

    @Test
    public void twoLine() throws IOException {
        runLogParserTest(
                "12|-af\n" +
                "af|-",

                "0|-1|-12\n" +
                "1|-|-af"
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
                "a8|-",

                "0|-3 1|-a0\n" +
                "1|-2 4|-a1\n" +
                "2|-3 5 8|-a2\n" +
                "3|-6|-a3\n" +
                "4|-7|-a4\n" +
                "5|-7|-a5\n" +
                "6|-7|-a6\n" +
                "7|-|-a7\n" +
                "8|-|-a8"
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

                "0|-3 1|-a0\n" +
                "1|-2 4|-a1\n" +
                "2|-3 5 _|-a2\n" +
                "3|-6|-a3\n" +
                "4|-_|-a4\n" +
                "5|-_|-a5\n" +
                "6|-_|-a6",

                false
        );
    }

}
