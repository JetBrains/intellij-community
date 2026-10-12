// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class ConditionalExpressionFormatterTest : JavaFormatterTestCase() {
  private val commonSettings: CommonCodeStyleSettings
    get() = getSettings(JavaLanguage.INSTANCE)

  override fun getBasePath(): String = "psi/formatter/conditionalExpression"

  @Test
  fun testLiteralOperatorOnPreviousLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    doTest()
  }

  @Test
  fun testLiteralOperatorOnNewLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    doTest()
  }

  @Test
  fun testLiteralInParensOperatorOnPreviousLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    doTest()
  }

  @Test
  fun testLiteralInParensOperatorOnNewLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    doTest()
  }

  @Test
  fun testLiteralPartialInParensOperatorOnPreviousLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    doTest()
  }

  @Test
  fun testLiteralPartialInParensOperatorOnNewLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    doTest()
  }

  @Test
  fun testCallChainOperatorOnPreviousLine() {
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  @Test
  fun testCallChainOperatorOnNewLine() {
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  @Test
  fun testCallChainInParensOperatorOnPreviousLine() {
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  @Test
  fun testCallChainInParensOperatorOnNewLine() {
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  @Test
  fun testCallChainPartialInParensOperatorOnPreviousLine() {
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  @Test
  fun testCallChainPartialInParensOperatorOnNewLine() {
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  @Test
  fun testMixedOperatorOnPreviousLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  @Test
  fun testMixedOperatorOnNewLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  @Test
  fun testMixedInParensOperatorOnPreviousLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  @Test
  fun testMixedInParensOperatorOnNewLine() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    commonSettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  @Test
  fun testTernaryOperatorInsideTernaryOperator() {
    commonSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    doTest()
  }

  @Test
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