// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.codeStyle.CommonCodeStyleSettings

class ConditionalExpressionFormatterTest : JavaFormatterTestCase() {
  private val commonSettings: CommonCodeStyleSettings
    get() = getSettings(JavaLanguage.INSTANCE)

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
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  fun testMixedOperatorOnNewLine() {
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  fun testMixedInParensOperatorOnPreviousLine() {
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  fun testMixedInParensOperatorOnNewLine() {
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
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