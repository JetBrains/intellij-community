// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.lexer

import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.util.PsiUtil
import com.intellij.util.text.ByteArrayCharSequence
import com.intellij.util.text.CharArrayCharSequence
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JavaKeywordsTest {
  @Test fun hardAndSoft() {
    assertTrue(PsiUtil.isKeyword("char", LanguageLevel.JDK_1_9))
    assertFalse(PsiUtil.isSoftKeyword("char", LanguageLevel.JDK_1_9))
    assertFalse(PsiUtil.isKeyword("module", LanguageLevel.JDK_1_9))
    assertTrue(PsiUtil.isSoftKeyword("module", LanguageLevel.JDK_1_9))
    assertFalse(PsiUtil.isKeyword("sealed", LanguageLevel.JDK_17))
    assertTrue(PsiUtil.isSoftKeyword("sealed", LanguageLevel.JDK_17))
    assertFalse(PsiUtil.isKeyword("permits", LanguageLevel.JDK_17))
    assertTrue(PsiUtil.isSoftKeyword("permits", LanguageLevel.JDK_17))
    assertFalse(PsiUtil.isKeyword("when", LanguageLevel.JDK_20))
    assertFalse(PsiUtil.isSoftKeyword("when", LanguageLevel.JDK_20))
    assertFalse(PsiUtil.isKeyword("when", LanguageLevel.JDK_21))
    assertTrue(PsiUtil.isSoftKeyword("when", LanguageLevel.JDK_21))
  }

  @Test fun sequences() {
    assertTrue(PsiUtil.isSoftKeyword(ByteArrayCharSequence("module".toByteArray()), LanguageLevel.JDK_1_9))
    assertTrue(PsiUtil.isSoftKeyword(CharArrayCharSequence("[module]".toCharArray(), 1, 7), LanguageLevel.JDK_1_9))
  }
}