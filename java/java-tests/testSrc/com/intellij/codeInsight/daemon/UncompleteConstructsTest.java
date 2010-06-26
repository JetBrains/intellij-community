package com.intellij.codeInsight.daemon;

public class UncompleteConstructsTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/uncompleteConstructs";

  public void testIf1() throws Exception { doTest(); }
  public void testIf2() throws Exception { doTest(); }
  public void testIf3() throws Exception { doTest(); }
  public void testIf4() throws Exception { doTest(); }
  public void testIf5() throws Exception { doTest(); }
  public void testIf6() throws Exception { doTest(); }

  public void testWhile1() throws Exception { doTest();}
  public void testWhile2() throws Exception { doTest();}
  public void testWhile3() throws Exception { doTest();}
  public void testWhile4() throws Exception { doTest();}
  public void testWhile5() throws Exception { doTest();}

  public void testFor1() throws Exception { doTest(); }
  public void testFor2() throws Exception { doTest(); }
  public void testFor3() throws Exception { doTest(); }
  public void testFor4() throws Exception { doTest(); }
  public void testFor5() throws Exception { doTest(); }
  public void testFor6() throws Exception { doTest(); }
  public void testFor7() throws Exception { doTest(); }
  public void testFor8() throws Exception { doTest(); }
  public void testFor9() throws Exception { doTest(); }
  public void testFor10() throws Exception { doTest(); }

  public void testReturn1() throws Exception { doTest(); }
  public void testReturn2() throws Exception { doTest(); }

  private void doTest() throws Exception {
    doTest(getTestName(false)+".java");
  }
  protected void doTest(String filePath) throws Exception {
    super.doTest(BASE_PATH + "/" + filePath, false, false);
  }
}