// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.codeStyle.CommonCodeStyleSettings

class JavaFormatterNewLineAfterLBraceTest : JavaFormatterTestCase() {
  override fun getBasePath(): String = "psi/formatter/java/newLineAfterLBrace"

  private val commonSettings: CommonCodeStyleSettings
    get() = getSettings(JavaLanguage.INSTANCE)

  override fun setUp() {
    super.setUp()
    commonSettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    commonSettings.RIGHT_MARGIN = 100
  }

  fun testWrapAlways() {
    commonSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    doIdempotentTest()
  }

  fun testChopDownIfLongMultipleLines() {
    commonSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    doIdempotentTest()
  }

  fun testChopDownIfLongSingleLine() {
    commonSettings.RIGHT_MARGIN = 200
    commonSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    doIdempotentTest()
  }

  fun testDoNotWrap() {
    commonSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    doTest()
  }

  fun testWrapAsNeedSingleLine() {
    commonSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    doIdempotentTest()
  }

  fun testWrapAsNeedMultipleLines() {
    commonSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED
    doIdempotentTest()
  }

  fun testMultipleCalls() {
    commonSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    doIdempotentTest()
  }

  private fun doIdempotentTest() {
    val testName = getTestName(false)
    doTest(testName, "${testName}_after")
    doTest("${testName}_after", "${testName}_after")
  }
}