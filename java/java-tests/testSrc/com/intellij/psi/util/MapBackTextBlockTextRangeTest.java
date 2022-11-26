// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public class MapBackTextBlockTextRangeTest extends LightPlatformCodeInsightTestCase {

  public void testEmpty() {
    doTest("\"\"\"\n\"\"\"", 0, 0, new TextRange(4, 4));
  }

  public void testExtraCharsBeforeContent() {
    doTest("\"\"\"   \n\"\"\"", 0, 0, new TextRange(7, 7));
  }

  public void testOneLineContentNoTrailingLine() {
    doTest("\"\"\"\n   foo  \"\"\"", 0, 5, new TextRange(7, 12));
  }

  public void testOneLineContentTrailingLine() {
    doTest("\"\"\"\nfoo  \n \"\"\"", 0, 3, new TextRange(4, 7));
  }

  public void testTrailingLineWithContent() {
    doTest("\"\"\"\nfoo  \n b\"\"\"", 0, 6, new TextRange(4, 12));
  }

  public void testContentWithBlankLines() {
    doTest("""
             ""\"
                 \s
              foo \t\f
                 \s
                    ""\"""", 0, 6, new TextRange(9, 24));
  }

  public void testIndentNonZero() {
    doTest("\"\"\"\n foo\n bar\n \"\"\"", 0, 8, new TextRange(5, 14));
  }

  public void testContentWithEscapeSequences() {
    doTest("\"\"\"\n\\u005c\\u005c\\u005c\\u005c\"\"\"", 1, 2, new TextRange(10, 16));
  }

  public void testInvalidRange() {
    doTest("\"\"\"\n\"\"\"", 0, 1, null);
  }

  private void doTest(String blockText, int from, int to, TextRange expectedRange) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
    PsiLiteralExpression textBlock = (PsiLiteralExpression)factory.createExpressionFromText(blockText, null);
    assertTrue(textBlock.isTextBlock());
    int indent = PsiLiteralUtil.getTextBlockIndent(textBlock);
    assertTrue(indent >= 0);
    TextRange actualRange = PsiLiteralUtil.mapBackTextBlockRange(textBlock.getText(), from, to, indent);
    assertEquals(expectedRange, actualRange);
  }
}
