package com.intellij.codeInsight.daemon.quickFix;

/**
 * @author ven
 */
public class CreateInnerClassFromUsageTest extends LightQuickFixTestCase{
  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createInnerClassFromUsage";
  }
}
