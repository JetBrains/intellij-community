// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.testFramework.RunAll
import groovy.transform.CompileStatic
import org.junit.Test

import static org.junit.Assert.assertEquals

@CompileStatic
class CalculatorSEContributorTest {

  private static void doTest(Map<String, String> data) {
    RunAll.runAll(data) { input, result ->
      assertEquals(input, result, CalculatorSEContributorKt.evaluate(input))
    }
  }

  @Test
  void bases() {
    doTest([
      "00"                : "0",
      "00000"             : "0",
      "0x0"               : "0",
      "0x0000"            : "0",
      "0b0"               : "0",
      "0b0000"            : "0",
      "0b10"              : "2",
      "012345670"         : "2739128",
      "0x1234567890ABCDEF": "1.311768467E18",
      "0xDBE"             : "3518",
    ])
  }

  @Test
  void operators() {
    doTest([
      "2 - 3"  : "-1",
      "2 + 3"  : "5",
      "2 * 3"  : "6",
      "2 / 3"  : "0.666666667",
      "2 ^ 0"  : "1",
      "2 ^ 3"  : "8",
      "2 ^ 0.5": "1.414213562",
      "2 ^ -1" : "0.5",
    ])
  }

  @Test
  void functions() {
    doTest([
      "sqrt 64": "8",
      "sin 0"  : "0",
      "sin 90" : "1",
      "cos 0"  : "1",
      "cos 90" : "0",
      "tan 0"  : "0",
      "tan 90" : "1.633123935E16",
    ])
  }

  @Test
  void special() {
    doTest([
      "1 / 0"  : "∞",
      "-1 / 0" : "-∞",
      "0/0"    : "NaN",
      "sqrt -1": "NaN",
    ])
  }

  @Test
  void precedence() {
    doTest([
      "2 + 2 * 2"   : "6",
      "(2 + 2) * 2" : "8",
      "2 * 2 ^ 2"   : "8",
      "(2 * 2) ^ 2" : "16",
      "sqrt 9 * 4"  : "12",
      "sqrt (9 * 4)": "6",
      "((((42))))"  : "42",
    ])
  }

  @Test
  void 'round and format'() {
    doTest([
      "-1000000000000.1": "-1E12",
      "-1000000000000"  : "-1E12",
      "-1000000000.1"   : "-1E9",
      "-1000000000"     : "-1E9",
      "-999999999.9"    : "-999999999.9",
      "-999999999"      : "-999999999",
      "-0.000000001"    : "-0.000000001",
      "-0.0000000005"   : "-0.000000001",
      "-0.0000000001"   : "0",
      "0"               : "0",
      "0.0"             : "0",
      "0.0000000001"    : "0",
      "0.0000000005"    : "0.000000001",
      "0.000000001"     : "0.000000001",
      "0.1 + 0.2"       : "0.3",
      "0.3 + 0.7"       : "1",
      "10"              : "10",
      "10.0"            : "10",
      "999999999"       : "999999999",
      "999999999.9"     : "999999999.9",
      "1000000000"      : "1E9",
      "1000000000.1"    : "1E9",
      "1000000000000"   : "1E12",
      "1000000000000.1" : "1E12",
    ])
  }
}
