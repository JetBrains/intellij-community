package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixAvailabilityTestCase;

/**
 * User: anna
 * Date: 10/7/11
 */
public class ImplementAbstractFromNewTest extends LightQuickFixAvailabilityTestCase {
  public void test() throws Exception {
    doAllTests();
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/implementAbstractFromNew";
  }
}
