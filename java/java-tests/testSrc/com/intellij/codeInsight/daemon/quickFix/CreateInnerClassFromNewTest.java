package com.intellij.codeInsight.daemon.quickFix;

/**
 * @author yole
 */
public class CreateInnerClassFromNewTest extends LightQuickFixTestCase {
  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createInnerClassFromNew";
  }
}