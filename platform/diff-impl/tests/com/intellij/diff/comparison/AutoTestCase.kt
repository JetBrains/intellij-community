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

import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.text.CharSequenceSubSequence
import java.util.Random
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertTrue

public abstract class AutoTestCase : UsefulTestCase() {
  protected val RNG: Random = Random()

  private var gotSeedException = false

  protected fun generateText(maxLength: Int, charCount: Int, predefinedChars: Map<Int, Char>): String {
    val length = RNG.nextInt(maxLength + 1)
    val builder = StringBuilder(length)

    for (i in 1..length) {
      val rnd = RNG.nextInt(charCount)
      val char = predefinedChars.get(rnd) ?: (rnd + 97).toChar();
      builder.append(char)
    }
    return builder.toString()
  }

  protected fun getCurrentSeed(): Long {
    if (gotSeedException) return -1
    try {
      val seedField = RNG.javaClass.getDeclaredField("seed")
      seedField.setAccessible(true)
      val seedFieldValue = seedField.get(RNG) as AtomicLong
      return seedFieldValue.get() xor 0x5DEECE66DL
    }
    catch (e: Exception) {
      gotSeedException = true
      System.err.println("Can't get random seed: " + e.getMessage())
      return -1
    }
  }

  public fun textToReadableFormat(text: CharSequence?): String {
    if (text == null) return "null"
    return "'" + text.toString().replace('\n', '*').replace('\t', '+') + "'"
  }

  public fun assertEqualsCharSequences(chunk1: CharSequence, chunk2: CharSequence, ignoreSpaces: Boolean, skipLastNewline: Boolean) {
    if (ignoreSpaces) {
      assertTrue(StringUtil.equalsIgnoreWhitespaces(chunk1, chunk2))
    }
    else {
      if (skipLastNewline) {
        if (StringUtil.equals(chunk1, chunk2)) return
        if (StringUtil.equals(stripNewline(chunk1), chunk2)) return
        if (StringUtil.equals(chunk1, stripNewline(chunk2))) return
        assertTrue(false)
      }
      else {
        assertTrue(StringUtil.equals(chunk1, chunk2))
      }
    }
  }

  private fun stripNewline(text: CharSequence): CharSequence? {
    return when (StringUtil.endsWithChar(text, '\n') ) {
      true -> CharSequenceSubSequence(text, 0, text.length() - 1)
      false -> null
    }
  }

  protected fun Int.until(a: Int): IntRange = this..a - 1
}
