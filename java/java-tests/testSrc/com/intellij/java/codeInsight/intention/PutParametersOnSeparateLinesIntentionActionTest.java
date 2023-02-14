// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.intention;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.LightIntentionActionTestCase;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;

public class PutParametersOnSeparateLinesIntentionActionTest extends LightIntentionActionTestCase {

  private boolean myBreakAfterLparen;
  private boolean myBreakBeforeRparen;

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/lists/putParametersOnSeparateLines";
  }

  @Override
  protected void beforeActionStarted(final String testName, final String contents) {
    super.beforeActionStarted(testName, contents);
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    myBreakAfterLparen = settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE;
    myBreakBeforeRparen = settings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE;
    settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = contents.contains("break after lparen");
    settings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = contents.contains("break before rparen");
  }

  @Override
  protected void afterActionCompleted(final String testName, final String contents) {
    super.afterActionCompleted(testName, contents);
    CommonCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = myBreakAfterLparen;
    settings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = myBreakBeforeRparen;
  }
}
