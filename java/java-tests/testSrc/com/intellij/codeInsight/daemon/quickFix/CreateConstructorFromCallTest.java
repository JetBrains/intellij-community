package com.intellij.codeInsight.daemon.quickFix;

/**
 * @author ven
 */
public class CreateConstructorFromCallTest extends LightQuickFixTestCase {

  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createConstructorFromCall";
  }
}
