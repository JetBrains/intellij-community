// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.siyeh.ig.LightInspectionTestCase;
import org.junit.Test;

import static com.intellij.psi.util.PsiLiteralUtil.escapeTextBlockCharacters;
import static org.junit.Assert.*;

/**
 * @author Bas Leijdekkers
 */
public class PsiLiteralUtilTest {

  @Test
  public void testEscapeTextBlockCharacters() {
    assertEquals("\\\"\"\"\\\"\"\"\\\"\\\"", escapeTextBlockCharacters("\"\"\"\"\"\"\"\"", false, true));
    assertEquals("\\\\", escapeTextBlockCharacters("\\", false, true));
    assertEquals("", escapeTextBlockCharacters("", false, true));
  }
}