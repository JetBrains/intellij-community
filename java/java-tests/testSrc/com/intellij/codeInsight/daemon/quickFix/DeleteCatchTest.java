package com.intellij.codeInsight.daemon.quickFix;

public class DeleteCatchTest extends LightQuickFixParameterizedTestCase {
  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/deleteCatch";
  }
}

