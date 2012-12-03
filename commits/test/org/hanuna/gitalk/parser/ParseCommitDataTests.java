package org.hanuna.gitalk.parser;

import org.hanuna.gitalk.commitmodel.builder.CommitLogData;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static org.hanuna.gitalk.CommitTestUtils.toStr;

/**
 * @author erokhins
 */
public class ParseCommitDataTests {
    private void runTestParseCommitData(String in, String out) {
        CommitLogData cd = GitLogParser.parseCommitData(in);
        assertEquals(out, toStr(cd));
    }


    @Test
    public void simple() throws Exception {
        runTestParseCommitData("a|-|-|-|-", "a|-|-|-0|-");
    }

    @Test
    public void simple2() throws Exception {
        runTestParseCommitData("a|-b c|-|-13|-message", "a|-b c|-|-13|-message");
    }

    @Test
    public void fullInformation() throws Exception {
        runTestParseCommitData("a12f|-adf2|-author s|-132352112|- message", "a12f|-adf2|-author s|-132352112|- message");
    }

    @Test
    public void moreChildrens() throws Exception {
        runTestParseCommitData("adf23|-a1 a2 a3|-a|-|-mes|-age", "adf23|-a1 a2 a3|-a|-0|-mes|-age");
    }

    @Test
    public void testSpecSymbolsInMessage() throws Exception {
        runTestParseCommitData("adf23|-adf2|-a|-1|-mes|-age", "adf23|-adf2|-a|-1|-mes|-age");
    }

}
