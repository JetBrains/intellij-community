package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;


public class EnableOptimizeImportsOnTheFlyTest extends LightQuickFixTestCase {
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new UnusedImportLocalInspection()};
  }

  public void test() throws Exception { doAllTests(); }

  protected void doAction(final String text, final boolean actionShouldBeAvailable, final String testFullPath, final String testName)
    throws Exception {
    boolean old = CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY;

    try {
      CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = false;
      IntentionAction action = findActionWithText(text);
      if (action == null && actionShouldBeAvailable) {
        fail("Action with text '" + text + "' is not available in test " + testFullPath);
      }
      if (action != null && actionShouldBeAvailable) {
        action.invoke(getProject(), getEditor(), getFile());
        assertTrue(CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY);
      }
    }
    finally {
      CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY = old;
    }
  }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/enableOptimizeImportsOnTheFly";
  }
}

