package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;

public class SimplifyBooleanExpressionTest extends LightIntentionActionTestCase {
  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/simplifyBooleanExpression";
  }
}

