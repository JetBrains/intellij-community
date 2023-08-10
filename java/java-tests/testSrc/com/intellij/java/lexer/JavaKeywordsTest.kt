// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.lexer

import com.intellij.lang.java.lexer.JavaLexer
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.text.ByteArrayCharSequence
import com.intellij.util.text.CharArrayCharSequence
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JavaKeywordsTest {
  @Test fun hardAndSoft() {
    assertTrue(JavaLexer.isKeyword("char", LanguageLevel.JDK_1_9))
    assertFalse(JavaLexer.isSoftKeyword("char", LanguageLevel.JDK_1_9))
    assertFalse(JavaLexer.isKeyword("module", LanguageLevel.JDK_1_9))
    assertTrue(JavaLexer.isSoftKeyword("module", LanguageLevel.JDK_1_9))
    assertFalse(JavaLexer.isKeyword("sealed", LanguageLevel.JDK_17))
    assertTrue(JavaLexer.isSoftKeyword("sealed", LanguageLevel.JDK_17))
    assertFalse(JavaLexer.isKeyword("permits", LanguageLevel.JDK_17))
    assertTrue(JavaLexer.isSoftKeyword("permits", LanguageLevel.JDK_17))
    assertFalse(JavaLexer.isKeyword("when", LanguageLevel.JDK_20_PREVIEW))
    assertTrue(JavaLexer.isSoftKeyword("when", LanguageLevel.JDK_20_PREVIEW))
    assertFalse(JavaLexer.isKeyword("when", LanguageLevel.JDK_21))
    assertTrue(JavaLexer.isSoftKeyword("when", LanguageLevel.JDK_21))
  }

  @Test fun sequences() {
    assertTrue(JavaLexer.isSoftKeyword(ByteArrayCharSequence("module".toByteArray()), LanguageLevel.JDK_1_9))
    assertTrue(JavaLexer.isSoftKeyword(CharArrayCharSequence("[module]".toCharArray(), 1, 7), LanguageLevel.JDK_1_9))
  }
}