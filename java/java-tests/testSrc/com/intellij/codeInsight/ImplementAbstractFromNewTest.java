package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixAvailabilityTestCase;

public class ImplementAbstractFromNewTest extends LightQuickFixAvailabilityTestCase {
  public void test() throws Exception {
    doAllTests();
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/implementAbstractFromNew";
  }
}
