// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

class ConditionalExpressionFormatterTest : JavaFormatterTestCase() {
  override fun getBasePath(): String = "psi/formatter/conditionalExpression"

  fun testLiteralOperatorOnPreviousLine() {
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    doTest()
  }

  fun testLiteralOperatorOnNewLine() {
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    doTest()
  }

  fun testLiteralInParensOperatorOnPreviousLine() {
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    doTest()
  }

  fun testLiteralInParensOperatorOnNewLine() {
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    doTest()
  }

  fun testLiteralPartialInParensOperatorOnPreviousLine() {
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    doTest()
  }

  fun testLiteralPartialInParensOperatorOnNewLine() {
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    doTest()
  }

  fun testCallChainOperatorOnPreviousLine() {
    doTest()
  }

  fun testCallChainOperatorOnNewLine() {
    doTest()
  }

  fun testCallChainInParensOperatorOnPreviousLine() {
    doTest()
  }

  fun testCallChainInParensOperatorOnNewLine() {
    doTest()
  }

  fun testCallChainPartialInParensOperatorOnPreviousLine() {
    doTest()
  }

  fun testCallChainPartialInParensOperatorOnNewLine() {
    doTest()
  }

  fun testMixedOperatorOnPreviousLine() {
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    doTest()
  }

  fun testMixedOperatorOnNewLine() {
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    doTest()
  }

  fun testMixedInParensOperatorOnPreviousLine() {
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    doTest()
  }

  fun testMixedInParensOperatorOnNewLine() {
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
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