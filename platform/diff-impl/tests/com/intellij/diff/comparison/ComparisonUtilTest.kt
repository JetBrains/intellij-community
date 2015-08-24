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

import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase

public class ComparisonUtilTest : UsefulTestCase() {
  public fun testTrimEquals() {
    doTestTrim(true, "", "")
    doTestTrim(true, "", "   ")
    doTestTrim(true, "   ", "   ")
    doTestTrim(true, "\n   ", "  \n")
    doTestTrim(true, "asd ", "asd  ")
    doTestTrim(true, "    asd", "asd")
    doTestTrim(true, "\n\n\n", "\n\n\n")
    doTestTrim(true, "\n  \n  \n ", "  \n \n\n  ")
    doTestTrim(false, "\n\n", "\n\n\n")

    doTestTrim(false, "\nasd ", "asd\n  ")
    doTestTrim(true, "\nasd \n", "\n asd\n  ")
    doTestTrim(false, "x", "y")
    doTestTrim(false, "\n", " ")

    doTestTrim(true, "\t ", "")
    doTestTrim(false, "", "\t\n \n\t")
    doTestTrim(false, "\t", "\n")

    doTestTrim(true, "x", " x")
    doTestTrim(true, "x", "x ")
    doTestTrim(false, "x\n", "x")

    doTestTrim(false, "abc", "a\nb\nc\n")
    doTestTrim(true, "\nx y x\n", "\nx y x\n")
    doTestTrim(false, "\nxyx\n", "\nx y x\n")
    doTestTrim(true, "\nx y x", "\nx y x")
    doTestTrim(false, "\nxyx", "\nx y x")
    doTestTrim(true, "x y x", "x y x")
    doTestTrim(false, "xyx", "x y x")
    doTestTrim(true, "  x y x  ", "x y x")

    doTestTrim(false, "x", "\t\n ")
    doTestTrim(false, "", " x ")
    doTestTrim(false, "", "x ")
    doTestTrim(false, "", " x")
    doTestTrim(false, "xyx", "xxx")
    doTestTrim(false, "xyx", "xYx")
  }

  private fun doTestTrim(expected: Boolean, string1: String, string2: String) {
    doTest(expected, string1, string2, ComparisonPolicy.TRIM_WHITESPACES);
  }

  private fun doTest(expected: Boolean, string1: String, string2: String, policy: ComparisonPolicy) {
    val result = MANAGER.isEquals(string1, string2, policy);
    TestCase.assertEquals("---\n" + string1 + "\n---\n" + string2 + "\n---", expected, result);
  }

  companion object {
    private val MANAGER = ComparisonManagerImpl()
  }
}
