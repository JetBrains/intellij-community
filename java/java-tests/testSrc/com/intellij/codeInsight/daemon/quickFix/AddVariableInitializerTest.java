package com.intellij.codeInsight.daemon.quickFix;

public class AddVariableInitializerTest extends LightQuickFixTestCase {

  public void test() throws Exception {
    doAllTests();
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/addVariableInitializer";
  }

}

