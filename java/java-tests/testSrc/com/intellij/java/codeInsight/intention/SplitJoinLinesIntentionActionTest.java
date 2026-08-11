// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;

public class SplitJoinLinesIntentionActionTest extends LightQuickFixParameterizedTestCase {

  private boolean myArrayInitializerLbraceOnNextLine;
  private boolean myArrayInitializerRbraceOnNextLine;
  private boolean myNewLineAfterLparenInAnnotation;
  private boolean myRparenOnNewLineInAnnotation;

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/splitJoinLines";
  }

  @Override
  protected void beforeActionStarted(String testName, String contents) {
    super.beforeActionStarted(testName, contents);
    CodeStyleSettings codeStyleSettings = CodeStyle.getSettings(getProject());
    final CommonCodeStyleSettings settings = codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE);
    myArrayInitializerLbraceOnNextLine = settings.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE;
    myArrayInitializerRbraceOnNextLine = settings.ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE;
    JavaCodeStyleSettings customSettings = codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class);
    myNewLineAfterLparenInAnnotation = customSettings.NEW_LINE_AFTER_LPAREN_IN_ANNOTATION;
    myRparenOnNewLineInAnnotation = customSettings.RPAREN_ON_NEW_LINE_IN_ANNOTATION;
    boolean needsBreak = testName.contains("Break");
    settings.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = needsBreak;
    settings.ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = needsBreak;
    customSettings.NEW_LINE_AFTER_LPAREN_IN_ANNOTATION = needsBreak;
    customSettings.RPAREN_ON_NEW_LINE_IN_ANNOTATION = needsBreak;
  }

  @Override
  protected void afterActionCompleted(String testName, String contents) {
    super.afterActionCompleted(testName, contents);
    CodeStyleSettings codeStyleSettings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings settings = codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE);
    settings.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = myArrayInitializerLbraceOnNextLine;
    settings.ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = myArrayInitializerRbraceOnNextLine;
    JavaCodeStyleSettings customSettings = codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class);
    customSettings.NEW_LINE_AFTER_LPAREN_IN_ANNOTATION = myNewLineAfterLparenInAnnotation;
    customSettings.RPAREN_ON_NEW_LINE_IN_ANNOTATION = myRparenOnNewLineInAnnotation;
  }
}
