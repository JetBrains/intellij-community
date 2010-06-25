package com.intellij.codeInsight.daemon.quickFix;

public class ChangeExtendsToImplementsTest extends LightQuickFix15TestCase {
  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/changeExtendsToImplements";
  }
}

