package org.hanuna.gitalk.log.parser;

import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.log.commit.parents.TimestampCommitParents;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class TimestampCommitParentsParserTest {

    private String toStr(TimestampCommitParents commitParents) {
        StringBuilder s = new StringBuilder();
        s.append(commitParents.getTimestamp()).append("|-");
        s.append(commitParents.getCommitHash().toStrHash()).append("|-");
        for (int i = 0; i < commitParents.getParentHashes().size(); i++) {
            Hash hash = commitParents.getParentHashes().get(i);
            if (i != 0) {
                s.append(" ");
            }
            s.append(hash.toStrHash());
        }
        return s.toString();
    }


    private void runTest(String inputStr) {
        TimestampCommitParents commitParents = CommitParser.parseTimestampParentHashes(inputStr);
        assertEquals(inputStr, toStr(commitParents));
    }

    @Test
    public void simple() {
        runTest("1|-af|-");
    }

    @Test
    public void parents() {
        runTest("12314|-af|-12 fd");
    }


    @Test
    public void parent() {
        runTest("12314|-af|-12");
    }


    @Test
    public void longTest() {
        runTest("123142412423412|-af|-12");
    }
}
