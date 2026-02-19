// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.testFramework.RunAll
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorSEContributorTest {

  private fun doTest(data: Map<String, String>) {
    RunAll.runAll(data) { input, result ->
      assertEquals(result, evaluate(input!!))
    }
  }

  @Test
  fun bases() {
    doTest(
      mapOf(
        "00" to "0",
        "00000" to "0",
        "0x0" to "0",
        "0x0000" to "0",
        "0b0" to "0",
        "0b0000" to "0",
        "0b10" to "2",
        "012345670" to "2739128",
        "0x1234567890ABCDEF" to "1.311768467E18",
        "0xDBE" to "3518",
      )
    )
  }

  @Test
  fun operators() {
    doTest(
      mapOf(
        "2 - 3" to "-1",
        "2 + 3" to "5",
        "2 * 3" to "6",
        "2 / 3" to "0.666666667",
        "2 ^ 0" to "1",
        "2 ^ 3" to "8",
        "2 ^ 0.5" to "1.414213562",
        "2 ^ -1" to "0.5",
      )
    )
  }

  @Test
  fun functions() {
    doTest(
      mapOf(
        "sqrt 64" to "8",
        "sin 0" to "0",
        "sin 90" to "1",
        "cos 0" to "1",
        "cos 90" to "0",
        "tan 0" to "0",
        "tan 90" to "1.633123935E16",
      )
    )
  }

  @Test
  fun special() {
    doTest(
      mapOf(
        "1 / 0" to "∞",
        "-1 / 0" to "-∞",
        "0/0" to "NaN",
        "sqrt -1" to "NaN",
      )
    )
  }

  @Test
  fun precedence() {
    doTest(
      mapOf(
        "2 + 2 * 2" to "6",
        "(2 + 2) * 2" to "8",
        "2 * 2 ^ 2" to "8",
        "(2 * 2) ^ 2" to "16",
        "sqrt 9 * 4" to "12",
        "sqrt (9 * 4)" to "6",
        "((((42))))" to "42",
      )
    )
  }

  @Test
  fun `round and format`() {
    doTest(
      mapOf(
        "-1000000000000.1" to "-1E12",
        "-1000000000000" to "-1E12",
        "-1000000000.1" to "-1E9",
        "-1000000000" to "-1E9",
        "-999999999.9" to "-999999999.9",
        "-999999999" to "-999999999",
        "-0.000000001" to "-0.000000001",
        "-0.0000000005" to "-0.000000001",
        "-0.0000000001" to "0",
        "0" to "0",
        "0.0" to "0",
        "0.0000000001" to "0",
        "0.0000000005" to "0.000000001",
        "0.000000001" to "0.000000001",
        "0.1 + 0.2" to "0.3",
        "0.3 + 0.7" to "1",
        "10" to "10",
        "10.0" to "10",
        "999999999" to "999999999",
        "999999999.9" to "999999999.9",
        "1000000000" to "1E9",
        "1000000000.1" to "1E9",
        "1000000000000" to "1E12",
        "1000000000000.1" to "1E12",
      )
    )
  }
}