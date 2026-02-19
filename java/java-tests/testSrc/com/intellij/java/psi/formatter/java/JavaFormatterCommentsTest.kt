// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

class JavaFormatterCommentsTest: JavaFormatterIdempotencyTestCase() {
  override fun getBasePath(): String {
    return "psi/formatter/java/comments"
  }

  fun testEndOfLineCommentWithMethodArgumentsAlignment() {
    commonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    doIdempotentTest()
  }

  fun testCStyleCommentWithMethodArgumentsAlignment() {
    commonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    doIdempotentTest()
  }
}