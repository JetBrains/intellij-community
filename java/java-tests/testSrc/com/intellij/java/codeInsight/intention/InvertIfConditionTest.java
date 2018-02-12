/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.intention;

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

  public void test() {
    doAllTests();
  }
}
