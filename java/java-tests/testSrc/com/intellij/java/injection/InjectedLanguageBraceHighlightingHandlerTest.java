package com.intellij.java.injection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.highlighting.BraceHighlightingHandlerTest;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;

public class InjectedLanguageBraceHighlightingHandlerTest extends InjectedLanguageTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private static final String BASE_PATH = "/injection/braceHighlighter/";

  public void testEmptyShred() {
    configureByFile(BASE_PATH + getTestName(true) + ".java");
    String result = BraceHighlightingHandlerTest.getEditorTextWithHighlightedBraces(getEditor(), getFile());
    UsefulTestCase.assertSameLinesWithFile(getAnswerFilePath(), result);
  }

  @Override
  protected @NotNull String getAnswerFilePath() {
    return getTestDataPath() + BASE_PATH + getTestName(true) + ".java.txt";
  }
}
