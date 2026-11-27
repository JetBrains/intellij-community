// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.editorActions;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class JavaEnterInStringLiteralTest {
  private static final String BASE_PATH = "/codeInsight/editorActions/stringLiteral/";

  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_21));
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void testNonIndentedTextBlockContent() {
    doTest();
  }

  @Test
  public void testContentAtTheStartOfTextBlock() {
    doTest();
  }

  @Test
  public void testOnlyWhitespacesTextBlock() {
    doTest();
  }

  @Test
  public void testContentAtTheEndOfTextBlock() {
    doTest();
  }

  @Test
  public void testEmptyTextBlock() {
    doTest();
  }

  @Test
  public void testEnter() {
    doTest();
  }

  @Test
  public void testEnterInInjectedStringBlockLiteralStart() {
    doTest();
  }

  @Test
  public void testEnterInInjectedStringBlockLiteralMiddle() {
    doTest();
  }

  @Test
  public void testEnterInInjectedStringBlockLiteralMiddleTabs() {
    CommonCodeStyleSettings settings = CodeStyle.getSettings(daemonFixture.get().getFixture().getProject()).getCommonSettings(JavaLanguage.INSTANCE);
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

  @Test
  public void testEnterInInjectedStringBlockLiteralEnd() {
    doTest();
  }

  @Test
  public void testEnterOpSignOnNextLine() {
    CommonCodeStyleSettings settings = CodeStyle.getSettings(daemonFixture.get().getFixture().getProject()).getCommonSettings(JavaLanguage.INSTANCE);
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
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    String testName = myFixture.getTestName(true);
    daemonFixture.get().configureByFile(BASE_PATH + testName + ".java");
    myFixture.runInCommand(() -> EditorTestUtil.performTypingAction(myFixture.getEditor(), '\n'));
    myFixture.checkResultByFile(BASE_PATH + testName + "_after.java");
  }
}
