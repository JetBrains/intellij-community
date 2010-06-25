package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFix15TestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.openapi.util.Comparing;

/**
 * @author yole
 */
public class I18nQuickFixTest extends LightQuickFix15TestCase {
  private boolean myMustBeAvailableAfterInvoke;

  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new I18nInspection()};
  }

  public void test() throws Exception {
    doAllTests();
  }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/i18n";
  }

  protected void beforeActionStarted(final String testName, final String contents) {
    myMustBeAvailableAfterInvoke = Comparing.strEqual(testName, "SystemCall.java");
  }

  protected boolean shouldBeAvailableAfterExecution() {
    return myMustBeAvailableAfterInvoke;
  }
}
