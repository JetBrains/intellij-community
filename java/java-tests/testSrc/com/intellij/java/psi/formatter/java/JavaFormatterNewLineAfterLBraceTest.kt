// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.psi.codeStyle.CommonCodeStyleSettings

class JavaFormatterNewLineAfterLBraceTest : JavaFormatterIdempotencyTestCase() {
  override fun getBasePath(): String = "psi/formatter/java/newLineAfterLBrace"

  override fun setUp() {
    super.setUp()
    commonSettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    commonSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    commonSettings.ALIGN_MULTILINE_PARAMETERS = false
    commonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false
    commonSettings.RIGHT_MARGIN = 100
  }

  fun testWrapAlways() {
    setupWrapType(CommonCodeStyleSettings.WRAP_ALWAYS)
    doIdempotentTest()
  }

  fun testChopDownIfLongMultipleLines() {
    setupWrapType(CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM)
    doIdempotentTest()
  }

  fun testChopDownIfLongSingleLine() {
    commonSettings.RIGHT_MARGIN = 200
    setupWrapType(CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM)
    doIdempotentTest()
  }

  fun testDoNotWrap() {
    setupWrapType(CommonCodeStyleSettings.DO_NOT_WRAP)
    doTest()
  }

  fun testWrapAsNeedSingleLine() {
    setupWrapType(CommonCodeStyleSettings.WRAP_AS_NEEDED)
    doIdempotentTest()
  }

  fun testWrapAsNeedMultipleLines() {
    setupWrapType(CommonCodeStyleSettings.WRAP_AS_NEEDED)
    doIdempotentTest()
  }

  fun testMultipleEntities() {
    setupWrapType(CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM)
    doIdempotentTest()
  }

  fun testWrapAlwaysRespectAlignWhenMultiline() {
    setupWrapType(CommonCodeStyleSettings.WRAP_ALWAYS)
    setupAlignWhenMultiline()
    doIdempotentTest()
  }

  fun testWrapAsNeededRespectAlignWhenMultiline() {
    setupWrapType(CommonCodeStyleSettings.WRAP_AS_NEEDED)
    setupAlignWhenMultiline()
    doIdempotentTest()
  }

  fun testChopDownIfLongRespectAlignWhenMultiline() {
    setupWrapType(CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM)
    setupAlignWhenMultiline()
    doIdempotentTest()
  }

  private fun setupAlignWhenMultiline() {
    commonSettings.ALIGN_MULTILINE_PARAMETERS = true
    commonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
  }

  private fun setupWrapType(wrapType: Int) {
    commonSettings.CALL_PARAMETERS_WRAP = wrapType
    commonSettings.METHOD_PARAMETERS_WRAP = wrapType
  }
}
