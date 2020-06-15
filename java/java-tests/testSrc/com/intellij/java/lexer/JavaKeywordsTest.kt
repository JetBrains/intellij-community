/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    assertFalse(JavaLexer.isKeyword("sealed", LanguageLevel.JDK_15_PREVIEW))
    assertTrue(JavaLexer.isSoftKeyword("sealed", LanguageLevel.JDK_15_PREVIEW))
    assertFalse(JavaLexer.isKeyword("permits", LanguageLevel.JDK_15_PREVIEW))
    assertTrue(JavaLexer.isSoftKeyword("permits", LanguageLevel.JDK_15_PREVIEW))
  }

  @Test fun sequences() {
    assertTrue(JavaLexer.isSoftKeyword(ByteArrayCharSequence.convertToBytesIfPossible("module"), LanguageLevel.JDK_1_9))
    assertTrue(JavaLexer.isSoftKeyword(CharArrayCharSequence("[module]".toCharArray(), 1, 7), LanguageLevel.JDK_1_9))
  }

  @Test fun nullTolerance() {
    assertFalse(JavaLexer.isKeyword(null, LanguageLevel.JDK_1_3))
    assertFalse(JavaLexer.isSoftKeyword(null, LanguageLevel.JDK_1_9))
  }
}