package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFix15TestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class I18nQuickFixTest extends LightQuickFix15TestCase {
  private boolean myMustBeAvailableAfterInvoke;

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new I18nInspection()};
  }

  public void test() throws Exception {
    doAllTests();
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/i18n";
  }

  @Override
  protected void beforeActionStarted(final String testName, final String contents) {
    myMustBeAvailableAfterInvoke = Comparing.strEqual(testName, "SystemCall.java");
  }

  @Override
  protected boolean shouldBeAvailableAfterExecution() {
    return myMustBeAvailableAfterInvoke;
  }
}
