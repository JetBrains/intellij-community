package com.intellij.codeInsight.daemon.quickFix;

public class GeneralizeCatchTest extends LightQuickFixTestCase {
  public void test() throws Exception {
    doAllTests();
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/generalizeCatch";
  }
}
