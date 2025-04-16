// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.JvmAutomaticTestMethodRenamerTestBase

class JavaAutomaticTestMethodRenamerTest : JvmAutomaticTestMethodRenamerTestBase() {
  override fun getBasePath(): String = JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/refactoring/renameTestMethod"

  fun testTestMethod() = doTest("ClassTest.java")

  fun testHelperMethod() = doTest("ClassTest.java")

  fun testHelperClass() = doTest("TestUtil.java")
}