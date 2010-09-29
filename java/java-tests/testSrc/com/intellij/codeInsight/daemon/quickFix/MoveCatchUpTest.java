
package com.intellij.codeInsight.daemon.quickFix;

public class MoveCatchUpTest extends LightQuickFixTestCase {

  public void test() throws Exception {
    doAllTests();
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/moveCatchUp";
  }

}

