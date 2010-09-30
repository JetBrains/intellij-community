package com.intellij.codeInsight.daemon.quickFix;

/**
 * @author ven
 */
public class CreateLocalFromUsageTest extends LightQuickFixTestCase {
  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createLocalFromUsage";
  }
}
