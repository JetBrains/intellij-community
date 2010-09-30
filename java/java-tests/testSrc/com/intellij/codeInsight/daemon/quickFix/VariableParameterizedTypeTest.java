package com.intellij.codeInsight.daemon.quickFix;

public class VariableParameterizedTypeTest extends LightQuickFix15TestCase {

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/variableParameterizedType";
  }

}

