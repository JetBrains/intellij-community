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
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

public class InvertIfConditionTest extends LightIntentionActionTestCase {

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  private static final String BASE_PATH = "/codeInsight/invertIfCondition/";
  private boolean myElseOnNewLine;

  @Override
  protected boolean shouldBeAvailableAfterExecution() {
    return true;
  }

  @Override
  protected void beforeActionStarted(final String testName, final String contents) {
    super.beforeActionStarted(testName, contents);
    final CommonCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    myElseOnNewLine = settings.ELSE_ON_NEW_LINE;
    settings.ELSE_ON_NEW_LINE = !contents.contains("else on the same line");
  }

  @Override
  protected void afterActionCompleted(final String testName, final String contents) {
    super.afterActionCompleted(testName, contents);
    CommonCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.ELSE_ON_NEW_LINE = myElseOnNewLine;
  }

  public void test() throws Exception {
    doAllTests();
  }
}
