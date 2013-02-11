package org.hanuna.gitalk.log.parser;

import org.hanuna.gitalk.log.commit.CommitParents;
import org.hanuna.gitalk.commit.Hash;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class CommitParserTest {
    private String toStr(CommitParents commitParentHashes) {
        StringBuilder s = new StringBuilder();
        s.append(commitParentHashes.getCommitHash().toStrHash()).append("|-");
        for (int i = 0; i < commitParentHashes.getParentHashes().size(); i++) {
            Hash hash = commitParentHashes.getParentHashes().get(i);
            if (i != 0) {
                s.append(" ");
            }
            s.append(hash.toStrHash());
        }
        return s.toString();
    }

    private void runTest(String inputStr) {
        CommitParents commitParents = CommitParser.parseCommitParents(inputStr);
        assertEquals(inputStr, toStr(commitParents));
    }

    @Test
    public void simple1() {
        runTest("a312|-");
    }

    @Test
    public void parent() {
        runTest("a312|-23");
    }

    @Test
    public void twoParent() {
        runTest("a312|-23 a54");
    }


    @Test
    public void moreParent() {
        runTest("a312|-23 a54 abcdef34 034f 00af 00000");
    }


}
