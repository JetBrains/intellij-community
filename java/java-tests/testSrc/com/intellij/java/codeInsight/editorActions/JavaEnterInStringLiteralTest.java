// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.editorActions;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;

public class JavaEnterInStringLiteralTest extends LightJavaCodeInsightTestCase {
  private static final String BASE_PATH = "/codeInsight/editorActions/stringLiteral/";

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_21;
  }

  public void testNonIndentedTextBlockContent() {
    doTest();
  }

  public void testContentAtTheStartOfTextBlock() {
    doTest();
  }

  public void testOnlyWhitespacesTextBlock() {
    doTest();
  }

  public void testContentAtTheEndOfTextBlock() {
    doTest();
  }

  public void testEmptyTextBlock() {
    doTest();
  }

  public void testEnter() {
    doTest();
  }

  public void testEnterInInjectedStringBlockLiteralStart() {
    doTest();
  }

  public void testEnterInInjectedStringBlockLiteralMiddle() {
    doTest();
  }

  public void testEnterInInjectedStringBlockLiteralMiddleTabs() {
    CommonCodeStyleSettings settings = getCurrentCodeStyleSettings().getCommonSettings(JavaLanguage.INSTANCE);
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions();
    boolean oldProperty = indentOptions.USE_TAB_CHARACTER;
    try {
      indentOptions.USE_TAB_CHARACTER = true;
      doTest();
    }
    finally {
      indentOptions.USE_TAB_CHARACTER = oldProperty;
    }
  }

  public void testEnterInInjectedStringBlockLiteralEnd() {
    doTest();
  }

  public void testEnterOpSignOnNextLine() {
    CommonCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    boolean opSignOnNextLine = settings.BINARY_OPERATION_SIGN_ON_NEXT_LINE;
    try {
      settings.BINARY_OPERATION_SIGN_ON_NEXT_LINE =  true;
      doTest();
    }
    finally {
      settings.BINARY_OPERATION_SIGN_ON_NEXT_LINE = opSignOnNextLine;
    }
  }

  private void doTest() {
    String testName = getTestName(true);
    configureByFile(BASE_PATH + testName + ".java");
    EditorTestUtil.performTypingAction(getEditor(), '\n');
    checkResultByFile(BASE_PATH + testName + "_after.java");
  }
}
