package com.intellij.codeInsight.daemon.quickFix;

/**
 * @author ven
 */
public class CreateConstructorFromSuperTest extends LightQuickFixTestCase {

  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createConstructorFromSuper";
  }

}
