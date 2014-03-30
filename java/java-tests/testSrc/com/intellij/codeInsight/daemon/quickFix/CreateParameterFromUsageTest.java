package com.intellij.codeInsight.daemon.quickFix;

/**
 * @author ven
 */
public class CreateParameterFromUsageTest extends LightQuickFixParameterizedTestCase {
  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createParameterFromUsage";
  }
}