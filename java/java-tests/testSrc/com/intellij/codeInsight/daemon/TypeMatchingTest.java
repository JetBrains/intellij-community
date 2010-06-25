package com.intellij.codeInsight.daemon;

public class TypeMatchingTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/typeMatching";

  public void testIf() throws Exception {
    doTest("If.java");
  }

  public void testWhile() throws Exception {
    doTest("While.java");
  }

  public void testFor() throws Exception {
    doTest("For1.java");
    doTest("For2.java");
  }

  public void testShortConstWithCast() throws Exception {
    doTest("ShortConstWithCast.java");
  }

  protected void doTest(String filePath) throws Exception {
    super.doTest(BASE_PATH + "/" + filePath, false, false);
  }
}