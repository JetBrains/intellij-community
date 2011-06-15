package com.intellij.codeInsight.daemon.quickFix;

/**
 * @author ven
 */
public class CreateFieldFromUsageTest extends LightQuickFixTestCase{

  public void testAnonymousClass() throws Exception { doSingleTest(); }
  public void testExpectedTypes() throws Exception { doSingleTest(); }
  public void testInterface() throws Exception { doSingleTest(); }
  public void testMultipleTypes() throws Exception { doSingleTest(); }
  public void testMultipleTypes2() throws Exception { doSingleTest(); }
  public void testParametericMethod() throws Exception { doSingleTest(); }
  public void testQualifyInner() throws Exception { doSingleTest(); }
  public void testSortByRelevance() throws Exception { doSingleTest(); }
  public void testTypeArgsFormatted() throws Exception { doSingleTest(); }

  protected void doSingleTest() {
    doSingleTest(getTestName(false) + ".java");
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createFieldFromUsage";
  }

}
