package com.intellij.codeInsight.daemon.quickFix;import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;

public class CreateCastExpressionFromInstanceofTest extends LightIntentionActionTestCase {
  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createCastExpressionFromInstanceof";
  }
}