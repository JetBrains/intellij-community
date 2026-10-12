// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class JavaFormatterNewLineAfterLBraceTest : JavaFormatterIdempotencyTestCase() {
  override fun getBasePath(): String = "psi/formatter/java/newLineAfterLBrace"

  @BeforeEach
  fun setUp() {
    commonSettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    commonSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    commonSettings.ALIGN_MULTILINE_PARAMETERS = false
    commonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false
    commonSettings.RIGHT_MARGIN = 100
  }

  @Test
  fun testWrapAlways() {
    setupWrapType(CommonCodeStyleSettings.WRAP_ALWAYS)
    doIdempotentTest()
  }

  @Test
  fun testChopDownIfLongMultipleLines() {
    setupWrapType(CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM)
    doIdempotentTest()
  }

  @Test
  fun testChopDownIfLongSingleLine() {
    commonSettings.RIGHT_MARGIN = 200
    setupWrapType(CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM)
    doIdempotentTest()
  }

  @Test
  fun testDoNotWrap() {
    setupWrapType(CommonCodeStyleSettings.DO_NOT_WRAP)
    doTest()
  }

  @Test
  fun testWrapAsNeedSingleLine() {
    setupWrapType(CommonCodeStyleSettings.WRAP_AS_NEEDED)
    doIdempotentTest()
  }

  @Test
  fun testWrapAsNeedMultipleLines() {
    setupWrapType(CommonCodeStyleSettings.WRAP_AS_NEEDED)
    doIdempotentTest()
  }

  @Test
  fun testMultipleEntities() {
    setupWrapType(CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM)
    doIdempotentTest()
  }

  @Test
  fun testWrapAlwaysRespectAlignWhenMultiline() {
    setupWrapType(CommonCodeStyleSettings.WRAP_ALWAYS)
    setupAlignWhenMultiline()
    doIdempotentTest()
  }

  @Test
  fun testWrapAsNeededRespectAlignWhenMultiline() {
    setupWrapType(CommonCodeStyleSettings.WRAP_AS_NEEDED)
    setupAlignWhenMultiline()
    doIdempotentTest()
  }

  @Test
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
