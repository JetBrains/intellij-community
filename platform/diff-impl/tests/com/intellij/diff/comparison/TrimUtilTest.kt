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
  private val PUNCTUATION = "(){}[],./?`~!@#$%^&*-=+|\\;:'\"<>";

  fun testPunctuation() {
    for (c in Character.MIN_VALUE..Character.MAX_VALUE) {
      doCheckPunctuation(c)
    }
  }

  private fun doCheckPunctuation(c: Char) {
    assertEquals(StringUtil.containsChar(PUNCTUATION, c), TrimUtil.isPunctuation(c), "'" + c + "' - " + c.toInt())
  }
}
