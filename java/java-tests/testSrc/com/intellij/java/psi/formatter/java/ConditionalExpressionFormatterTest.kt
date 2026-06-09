// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.codeStyle.CommonCodeStyleSettings

class ConditionalExpressionFormatterTest : JavaFormatterTestCase() {
  private val commonSettings: CommonCodeStyleSettings
    get() = getSettings(JavaLanguage.INSTANCE)

  override fun getBasePath(): String = "psi/formatter/conditionalExpression"

  fun testLiteralOperatorOnPreviousLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    doTest()
  }

  fun testLiteralOperatorOnNewLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    doTest()
  }

  fun testLiteralInParensOperatorOnPreviousLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    doTest()
  }

  fun testLiteralInParensOperatorOnNewLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    doTest()
  }

  fun testLiteralPartialInParensOperatorOnPreviousLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    doTest()
  }

  fun testLiteralPartialInParensOperatorOnNewLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    doTest()
  }

  fun testCallChainOperatorOnPreviousLine() {
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  fun testCallChainOperatorOnNewLine() {
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  fun testCallChainInParensOperatorOnPreviousLine() {
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  fun testCallChainInParensOperatorOnNewLine() {
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  fun testCallChainPartialInParensOperatorOnPreviousLine() {
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  fun testCallChainPartialInParensOperatorOnNewLine() {
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  fun testMixedOperatorOnPreviousLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  fun testMixedOperatorOnNewLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  fun testMixedInParensOperatorOnPreviousLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  fun testMixedInParensOperatorOnNewLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  fun testTernaryOperatorInsideTernaryOperator() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    doTest()
  }

  fun testMixedChainCallAndBinaryExpressionNoAlignment() {
    doTest()
  }


  override fun doTest() {
    val fileNameBefore = getTestName(true)
    val fileNameAfter = fileNameBefore + "_after"
    doTest(fileNameBefore, fileNameAfter)
    doTest(fileNameAfter, fileNameAfter)
    super.doTest()
  }
}