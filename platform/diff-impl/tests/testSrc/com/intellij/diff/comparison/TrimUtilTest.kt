/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.comparison

import com.intellij.diff.DiffTestCase
import com.intellij.openapi.util.text.StringUtil

class TrimUtilTest : DiffTestCase() {
  private val PUNCTUATION = "(){}[],./?`~!@#$%^&*-=+|\\;:'\"<>"

  fun testPunctuation() {
    for (c in Character.MIN_VALUE..Character.MAX_VALUE) {
      assertIsPunctuation(c, isPunctuation(c))
    }
  }

  fun testContinuousScript() {
    assertIsAlpha("!? \t\n+~", false)

    assertIsAlpha("AB12", true)
    assertIsAlpha("АБВ汉语日ひรไ", true)
    assertIsAlpha("óèäñĀ", true)
    assertIsAlpha("\r_\u0001", true)
    assertIsAlpha("$chSmile$chMan", true)

    assertIsContinuous("12_ABZ", false)
    assertIsContinuous("АБВ", false)
    assertIsContinuous("ʁit", false)
    assertIsContinuous("음훈", false)
    assertIsContinuous("óèäñĀ", false)
    assertIsContinuous("\r_\u0001", false)

    assertIsContinuous("象形文字", true)
    assertIsContinuous("อักษรไ", true)
    assertIsContinuous("ひらがなカタカナ日本語", true)
    assertIsContinuous("汉语漢語", true)
    assertIsContinuous("☺♥", true)
    assertIsContinuous("$chSmile$chMan", true)
    assertIsContinuous("\u200e\u200f\u061c", true)
  }

  private fun assertIsPunctuation(c: Char, actual: Boolean) {
    assertEquals(StringUtil.containsChar(PUNCTUATION, c), actual, "'" + c + "' - " + c.code)
  }

  private fun assertIsAlpha(text: String, expected: Boolean) {
    text.codePoints().forEach {
      assertEquals(expected, isAlpha(it), "$it - ${it.codePointValue}")
    }
  }

  private fun assertIsContinuous(text: String, expected: Boolean) {
    text.codePoints().forEach {
      assertEquals(expected, isContinuousScript(it), "$it - ${it.codePointValue}")
    }
  }

  private val Int.codePointValue: String get() = StringBuilder().appendCodePoint(this).toString()
}
