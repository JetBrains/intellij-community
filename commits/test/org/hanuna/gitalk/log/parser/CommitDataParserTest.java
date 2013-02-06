package org.hanuna.gitalk.log.parser;

import junit.framework.Assert;
import org.hanuna.gitalk.log.commitdata.CommitData;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class CommitDataParserTest {

    private String toStr(@NotNull CommitData commitData) {
        StringBuilder s = new StringBuilder();
        s.append(commitData.getAuthor()).append("|-");
        s.append(commitData.getTimeStamp()).append("|-");
        s.append(commitData.getMessage());
        return s.toString();
    }

    private void runTest(@NotNull String inputStr) {
        CommitData commitData = CommitParser.parseCommitData(inputStr);
        assertEquals(inputStr, toStr(commitData));
    }

    @Test
    public void simple1() {
        runTest("author|-1435|-message");
    }

    @Test
    public void emptyMessage() {
        runTest("author|-1435|-");
    }

    @Test
    public void longAuthor() {
        runTest("author  skdhfb  j 2353246|-1435|-message");
    }

    @Test
    public void strangeMessage() {
        runTest("author |-1435|-m|-|-es dfsage");
    }

    @Test
    public void bigTimestamp() {
        runTest("author |-143523623|-m|-|-es dfsage");
    }

    @Test
    public void emptyAuthor() {
        runTest("|-143523623|-");
    }

    @Test
    public void emptyTimestamp() {
        CommitData commitData = CommitParser.parseCommitData("author |-|-message");
        Assert.assertEquals("author ", commitData.getAuthor());
        Assert.assertEquals(0, commitData.getTimeStamp());
        Assert.assertEquals("message", commitData.getMessage());
    }

}
