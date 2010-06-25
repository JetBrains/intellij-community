package com.intellij.codeInsight.daemon.quickFix;

public class ChangeStringLiteralToCharInMethodCallTest extends LightQuickFixTestCase {

  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/stringToCharacterLiteral";
  }
}
