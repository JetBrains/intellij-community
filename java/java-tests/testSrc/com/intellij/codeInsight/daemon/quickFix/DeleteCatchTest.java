package com.intellij.codeInsight.daemon.quickFix;

public class DeleteCatchTest extends LightQuickFixTestCase {
  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/deleteCatch";
  }
}

