// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.codeStyle.CommonCodeStyleSettings

abstract class JavaFormatterIdempotencyTestCase : JavaFormatterTestCase() {
  protected val commonSettings: CommonCodeStyleSettings
    get() = getSettings(JavaLanguage.INSTANCE)

  protected fun doIdempotentTest() {
    val testName = getTestName(false)
    doTest(testName, "${testName}_after")
    doTest("${testName}_after", "${testName}_after")
  }
}