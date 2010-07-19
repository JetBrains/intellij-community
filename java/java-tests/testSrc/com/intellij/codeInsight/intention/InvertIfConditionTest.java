/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 22, 2002
 * Time: 2:58:42 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

public class InvertIfConditionTest extends LightIntentionActionTestCase {

  protected String getBasePath() {
    return BASE_PATH;
  }

  private static final String BASE_PATH = "/codeInsight/invertIfCondition/";
  private boolean myElseOnNewLine;

  protected boolean shouldBeAvailableAfterExecution() {
    return true;
  }

  protected void beforeActionStarted(final String testName, final String contents) {
    super.beforeActionStarted(testName, contents);
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    myElseOnNewLine = settings.ELSE_ON_NEW_LINE;
    settings.ELSE_ON_NEW_LINE = !contents.contains("else on the same line");
  }

  protected void afterActionCompleted(final String testName, final String contents) {
    super.afterActionCompleted(testName, contents);
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    settings.ELSE_ON_NEW_LINE = myElseOnNewLine;
  }

  public void test() throws Exception {
    doAllTests();
  }
}
