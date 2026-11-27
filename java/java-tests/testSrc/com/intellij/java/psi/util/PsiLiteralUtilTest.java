// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.util;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.psi.util.PsiLiteralUtil.escapeBackSlashesInTextBlock;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class PsiLiteralUtilTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR);

  @Test
  public void testEscapeTextBlockCharacters() {
    assertEquals("foo \\s\n", PsiLiteralUtil.escapeTextBlockCharacters("foo  \\n"));
    // escapes after 'bar' should be escaped since it's the last line in a text block
    assertEquals("foo \\s\nbar \\s", PsiLiteralUtil.escapeTextBlockCharacters("foo  \\nbar  "));

    assertEquals("", PsiLiteralUtil.escapeTextBlockCharacters(""));
    // last in line quote should be escaped
    assertEquals("\\\"", PsiLiteralUtil.escapeTextBlockCharacters("\""));
    assertEquals("\"\\\"", PsiLiteralUtil.escapeTextBlockCharacters("\"\""));
    // all escaped quotes should be unescaped
    assertEquals("\"\\\"", PsiLiteralUtil.escapeTextBlockCharacters("\\\"\""));
    // every third quote should be escaped
    assertEquals("\"\"\\\"\"\"\\\"\"\\\"", PsiLiteralUtil.escapeTextBlockCharacters("\"\"\"\"\"\"\"\""));


    // all sequences except new line should stay as is
    assertEquals("\\t\n", PsiLiteralUtil.escapeTextBlockCharacters("\\t\\n"));

    // trailing space before a real LF is escaped with \s (the new parseSpaces branch)
    assertEquals("foo\\s\nbar", PsiLiteralUtil.escapeTextBlockCharacters("foo \nbar"));
    assertEquals("foo\\s\nbar\\s", PsiLiteralUtil.escapeTextBlockCharacters("foo \nbar "));
    assertEquals("foo\\s\n", PsiLiteralUtil.escapeTextBlockCharacters("foo \n"));
    assertEquals("foo \\s", PsiLiteralUtil.escapeTextBlockCharacters("foo  "));
    // multiple spaces before real LF: all but last are kept, last replaced by \s
    assertEquals("foo  \\s\nbar", PsiLiteralUtil.escapeTextBlockCharacters("foo   \nbar"));
    // space at the very start of content followed by real LF
    assertEquals("\\s\nbar", PsiLiteralUtil.escapeTextBlockCharacters(" \nbar"));
    assertEquals(" foo\n bar\\s", PsiLiteralUtil.escapeTextBlockCharacters(" foo\n bar "));
  }

  @Test
  public void testEscapeBackSlashesInTextBlock() {
    assertEquals("", escapeBackSlashesInTextBlock(""));
    assertEquals("\\\\", escapeBackSlashesInTextBlock("\\"));
    // backslash before quote should be preserved
    assertEquals("\\\\\"", escapeBackSlashesInTextBlock("\\\""));
  }

  @Test
  public void testRemoveIncidentalWhitespacesInTextBlock() {
    assertEquals("", textBlockValue(" "));
    assertEquals("", textBlockValue("\\u0020"));
    assertEquals("", textBlockValue("\\uuuu0020"));
    assertEquals("", textBlockValue("\\u0020 "));
    assertEquals(" ", textBlockValue("\\040"));
    assertEquals("\\", textBlockValue("\\u005c\\\\u0020"));
    assertEquals("\\u0020", textBlockValue("\\\\u0020"));
    assertEquals("\\", textBlockValue("\\\\\\u0020"));
    assertEquals("\\\\u0020", textBlockValue("\\u005c\\u005c\\\\u0020"));
    assertEquals("", textBlockValue("	"));
    assertEquals("", textBlockValue("	 	"));
    assertEquals("", textBlockValue("\\u0009"));
    assertEquals("", textBlockValue("\\u0009	\\u0020 "));
    assertEquals("\\", textBlockValue("\\\\\\u0009"));
  }

  private static String textBlockValue(@NotNull String content) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(projectFixture.get().getProject());
    String blockText = "\"\"\"\n" +
                       content +
                       "\"\"\"";
    return ReadAction.computeBlocking(() -> {
      PsiLiteralExpression textBlock = (PsiLiteralExpression)factory.createExpressionFromText(blockText, null);
      return (String)textBlock.getValue();
    });
  }
}