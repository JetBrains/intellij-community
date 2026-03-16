// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public class MapBackTextBlockTextRangeTest extends LightPlatformCodeInsightTestCase {

  public void testEmpty() {
    doTest("\"\"\"\n\"\"\"", 0, 0, new TextRange(4, 4), "");
  }

  public void testExtraCharsBeforeContent() {
    doTest("\"\"\"   \n\"\"\"", 0, 0, new TextRange(7, 7), "");
  }

  public void testOneLineContentNoTrailingLine() {
    doTest("\"\"\"\n   foo  \"\"\"", 0, 5, new TextRange(7, 12), "foo  ");
  }

  public void testOneLineContentTrailingLine() {
    doTest("\"\"\"\nfoo  \n \"\"\"", 0, 3, new TextRange(4, 7), "foo");
  }

  public void testTrailingLineWithContent() {
    doTest("\"\"\"\nfoo  \n b\"\"\"", 0, 6, new TextRange(4, 12), "foo  \n b");
  }

  public void testContentWithBlankLines() {
    doTest("""
             ""\"
                 \s
              foo \t\f\\uu0020
                 \s
                    ""\"""", 0, 6, new TextRange(9, 31), "\n foo \t\f\\uu0020\n     \n");
  }

  public void testIndentNonZero() {
    doTest("\"\"\"\n foo\n bar\n \"\"\"", 0, 8, new TextRange(5, 14), "foo\n bar\n");
  }

  public void testContentWithEscapeSequences() {
    doTest("\"\"\"\n\\u005c\\u005c\\u005c\\u005c\"\"\"", 1, 2, new TextRange(10, 16), "\\u005c");
  }

  public void testInvalidRange() {
    doTest("\"\"\"\n\"\"\"", 0, 1, null, null);
  }

  public void testEscapedNewlines() {
    //noinspection TextBlockMigration
    String text = "\"\"\"   \n" +
                  "    %s\\\n" +
                  "    %t\\\\\\\n" + // <- three back-slashes and a newline
                  "    %u\\u0020\n" + // <- that's a space character
                  "    %v\\\n" +
                  "    \"\"\"";
    doTest(text, 0, 2, new TextRange(11, 13), "%s");
    doTest(text, 2, 4, new TextRange(19, 21), "%t");
    doTest(text, 5, 7, new TextRange(29, 31), "%u");
    doTest(text, 8, 10, new TextRange(42, 44), "%v");
  }

  public void testEscapedCharRange() {
    String text = "\"\"\"\n" + 
                  "    XXX\\uuu0030" +
                  "    \"\"\"";
    doTest(text, 3, 4, new TextRange(11, 19), "\\uuu0030");
  }

  private void doTest(String blockText, int from, int to, TextRange expectedRange, String expectedRangeText) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    PsiLiteralExpression textBlock = (PsiLiteralExpression)factory.createExpressionFromText(blockText, null);
    assertTrue(textBlock.isTextBlock());
    int indent = PsiLiteralUtil.getTextBlockIndent(textBlock);
    assertTrue(indent >= 0);
    assertEquals(expectedRange, PsiLiteralUtil.mapBackTextBlockRange(textBlock.getText(), from, to, indent));
    if (expectedRange != null) {
      assertEquals(StringUtil.escapeStringCharacters(expectedRangeText), 
                   StringUtil.escapeStringCharacters(expectedRange.substring(blockText)));
    }
  }
}
