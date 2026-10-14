// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class JavaFormatterCommentsTest: JavaFormatterIdempotencyTestCase() {
  override fun getBasePath(): String {
    return "psi/formatter/java/comments"
  }

  @Test
  fun testEndOfLineCommentWithMethodArgumentsAlignment() {
    commonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    doIdempotentTest()
  }

  @Test
  fun testCStyleCommentWithMethodArgumentsAlignment() {
    commonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    doIdempotentTest()
  }

  @Test
  fun testCommentBetweenAlignedMethodCallArguments() {
    commonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    doIdempotentTest()
  }
}