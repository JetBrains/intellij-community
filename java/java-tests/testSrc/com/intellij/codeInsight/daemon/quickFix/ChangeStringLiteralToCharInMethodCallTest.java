package com.intellij.codeInsight.daemon.quickFix;

public class ChangeStringLiteralToCharInMethodCallTest extends LightQuickFixParameterizedTestCase {

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/stringToCharacterLiteral";
  }
}
