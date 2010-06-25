package com.intellij.codeInsight.daemon;

public class VariableHighlightingTest extends DaemonAnalyzerTestCase{
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/variables";

  public void testMultiFieldDecl() throws Exception {
    doTest("MultiFieldDecl.java");
  }

  protected void doTest(String filePath) throws Exception {
    super.doTest(BASE_PATH + "/" + filePath, false, true);
  }
}