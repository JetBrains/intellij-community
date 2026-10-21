// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.testFramework.BraceHighlightingTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

/**
 * Checks the brace matching in a file without a language specific brace matcher.
 */
public class PlainTextBraceMatchingTest extends LightPlatformCodeInsightTestCase {
  public void testUnknownFileType() {
    configureFromFileText("cs.cs", "void a() <caret>{ print(\"b\"); <pair>}");
    BraceHighlightingTestUtil.assertBraceMatched(getFile(), getEditor());
  }

  public void testTxtWithNonMatchedBraceBetweenMatched() {
    configureFromFileText("a.txt", "<caret>{><pair>}");
    BraceHighlightingTestUtil.assertBraceMatched(getFile(), getEditor());
  }
}
