// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class SplitJoinLinesIntentionActionTest {
  private static final String TEST_DATA_PATH =
    JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/splitJoinLines";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightQuickFixFixture> quickFixFixture = lightQuickFixFixture(projectFixture, TEST_DATA_PATH);

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    LightQuickFixFixture quickFix = quickFixFixture.get();
    ActionHint hint = quickFix.configure(fileName);
    CodeStyleSettings codeStyleSettings = CodeStyle.getSettings(quickFix.getFixture().getProject());
    final CommonCodeStyleSettings settings = codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE);
    boolean myArrayInitializerLbraceOnNextLine = settings.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE;
    boolean myArrayInitializerRbraceOnNextLine = settings.ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE;
    JavaCodeStyleSettings customSettings = codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class);
    boolean myNewLineAfterLparenInAnnotation = customSettings.NEW_LINE_AFTER_LPAREN_IN_ANNOTATION;
    boolean myRparenOnNewLineInAnnotation = customSettings.RPAREN_ON_NEW_LINE_IN_ANNOTATION;
    boolean needsBreak = fileName.contains("Break");
    settings.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = needsBreak;
    settings.ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = needsBreak;
    customSettings.NEW_LINE_AFTER_LPAREN_IN_ANNOTATION = needsBreak;
    customSettings.RPAREN_ON_NEW_LINE_IN_ANNOTATION = needsBreak;
    try {
      quickFix.checkAction(fileName, hint);
    }
    finally {
      CodeStyleSettings afterCodeStyleSettings = CodeStyle.getSettings(quickFix.getFixture().getProject());
      CommonCodeStyleSettings afterSettings = afterCodeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE);
      afterSettings.ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE = myArrayInitializerLbraceOnNextLine;
      afterSettings.ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE = myArrayInitializerRbraceOnNextLine;
      JavaCodeStyleSettings afterCustomSettings = afterCodeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class);
      afterCustomSettings.NEW_LINE_AFTER_LPAREN_IN_ANNOTATION = myNewLineAfterLparenInAnnotation;
      afterCustomSettings.RPAREN_ON_NEW_LINE_IN_ANNOTATION = myRparenOnNewLineInAnnotation;
    }
  }
}
