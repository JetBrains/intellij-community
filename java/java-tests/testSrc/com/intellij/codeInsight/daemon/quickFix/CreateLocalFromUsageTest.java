package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

/**
 * @author ven
 */
public class CreateLocalFromUsageTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CodeStyleSettingsManager.getSettings(getProject()).GENERATE_FINAL_LOCALS = getTestName(true).contains("final");
  }

  @Override
  protected void tearDown() throws Exception {
    CodeStyleSettingsManager.getSettings(getProject()).GENERATE_FINAL_LOCALS = false;
    super.tearDown();
  }

  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createLocalFromUsage";
  }
}
