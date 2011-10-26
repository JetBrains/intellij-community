
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.openapi.application.ApplicationManager;

public class VariableAccessFromInnerClassTest extends LightQuickFixTestCase {
  public void test() throws Exception {
    doAllTests();
  }

  @Override
  protected void beforeActionStarted(String testName, String contents) {
    for (int i=0;i<10;i++) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          myEditor.getDocument().insertString(myEditor.getCaretModel().getOffset(), "//");
        }
      });

      doHighlighting();
      delete();
      delete();
      doHighlighting();
    }
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/mustBeFinal";
  }
}

