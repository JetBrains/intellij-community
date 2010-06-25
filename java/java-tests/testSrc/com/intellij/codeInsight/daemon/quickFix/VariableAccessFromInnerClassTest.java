
package com.intellij.codeInsight.daemon.quickFix;

public class VariableAccessFromInnerClassTest extends LightQuickFixTestCase {
  public void test() throws Exception {
    doAllTests();
  }

  @Override
  protected void beforeActionStarted(String testName, String contents) {
    for (int i=0;i<10;i++) {
      myEditor.getDocument().insertString(myEditor.getCaretModel().getOffset(), "//");
      doHighlighting();
      delete();
      delete();
      doHighlighting();
    }
  }

  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/mustBeFinal";
  }
}

