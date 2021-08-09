// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.JUnit5AssertionsConverterInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisTestsUtil

class JavaJUnit5AssertionsConverterInspectionTest : JUnit5AssertionsConverterInspectionTestBase() {
  override val fileExt: String = "java"

  override fun getTestDataPath() = JvmAnalysisTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/junit5assertionsconverter"

  fun `test AssertArrayEquals`() = testQuickFixAll("AssertArrayEquals")

  fun `test AssertArrayEquals message`() = testQuickFixAll("AssertArrayEqualsMessage")

  fun `test AssertEquals`() = testQuickFixAll("AssertEquals")

  fun `test AssertNotEqualsWithDelta`() = testQuickFixUnavailableAll("AssertNotEqualsWithDelta")

  fun `test AssertThat`() = testQuickFixAll("AssertThat")

  fun `test AssertTrue`() = testQuickFixAll("AssertTrue")

  fun `test AssertTrue method reference`() = testQuickFixAll("AssertTrueMethodRef")

  fun `test AssumeTrue`() = testQuickFixAll("AssumeTrue")
}