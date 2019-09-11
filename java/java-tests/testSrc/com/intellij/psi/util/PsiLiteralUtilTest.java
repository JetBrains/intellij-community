// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import org.junit.Test;

import static com.intellij.psi.util.PsiLiteralUtil.escapeBackSlashesInTextBlock;
import static org.junit.Assert.assertEquals;

/**
 * @author Bas Leijdekkers
 */
public class PsiLiteralUtilTest {

  @Test
  public void testEscapeTextBlockCharacters() {
    assertEquals("foo\\040\\040\n", PsiLiteralUtil.escapeTextBlockCharacters("foo  \\n"));
    // escapes after 'bar' should be escaped since it's the last line in a text block
    assertEquals("foo\\040\\040\nbar\\040\\040", PsiLiteralUtil.escapeTextBlockCharacters("foo  \\nbar  "));

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
  }

  @Test
  public void testEscapeBackSlashesInTextBlock() {
    assertEquals("", escapeBackSlashesInTextBlock(""));
    assertEquals("\\\\", escapeBackSlashesInTextBlock("\\"));
    // backslash before quote should be preserved
    assertEquals("\\\\\"", escapeBackSlashesInTextBlock("\\\""));
  }
}