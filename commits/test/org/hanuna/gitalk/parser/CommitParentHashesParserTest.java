package org.hanuna.gitalk.parser;

import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.log.commit.CommitAndParentHashes;
import org.hanuna.gitalk.log.commit.CommitParser;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class CommitParentHashesParserTest {
    private String toStr(CommitAndParentHashes commitParentHashes) {
        StringBuilder s = new StringBuilder();
        s.append(commitParentHashes.getCommitHash().toStrHash()).append("|-");
        for (int i = 0; i < commitParentHashes.getParentsHash().size(); i++) {
            Hash hash = commitParentHashes.getParentsHash().get(i);
            if (i != 0) {
                s.append(" ");
            }
            s.append(hash.toStrHash());
        }
        return s.toString();
    }

    private void runTest(String inputStr) {
        CommitAndParentHashes commitParentHashes = CommitParser.parseParentHashes(inputStr);
        assertEquals(inputStr, toStr(commitParentHashes));
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
