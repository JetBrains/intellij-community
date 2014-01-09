package com.intellij.codeInsight.daemon.quickFix;

public class AddNewArrayExpressionTest extends LightQuickFixParameterizedTestCase {
  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/addNewArrayExpression";
  }
}

