package com.intellij.codeInsight.daemon.quickFix;

public class RenameWrongReferenceTest extends LightQuickFixAvailabilityTestCase {

  public void test() throws Exception { doAllTests(); }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/renameWrongReference";
  }
}

