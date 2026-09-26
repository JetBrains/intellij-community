// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.util.PsiLiteralUtil.escapeBackSlashesInTextBlock;

/**
 * @author Bas Leijdekkers
 */
public class PsiLiteralUtilTest extends LightPlatformCodeInsightTestCase {

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

  public void testEscapeBackSlashesInTextBlock() {
    assertEquals("", escapeBackSlashesInTextBlock(""));
    assertEquals("\\\\", escapeBackSlashesInTextBlock("\\"));
    // backslash before quote should be preserved
    assertEquals("\\\\\"", escapeBackSlashesInTextBlock("\\\""));
  }

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

  private String textBlockValue(@NotNull String content) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    String blockText = "\"\"\"\n" +
                       content +
                       "\"\"\"";
    PsiLiteralExpression textBlock = (PsiLiteralExpression)factory.createExpressionFromText(blockText, null);
    return (String)textBlock.getValue();
  }
}