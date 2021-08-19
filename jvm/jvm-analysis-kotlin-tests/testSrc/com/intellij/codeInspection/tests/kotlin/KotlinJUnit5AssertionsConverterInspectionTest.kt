// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.JUnit5AssertionsConverterInspectionTestBase
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/junit5assertionsconverter"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinJUnit5AssertionsConverterInspectionTest : JUnit5AssertionsConverterInspectionTestBase() {
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  fun `test AssertArrayEquals`() {
    myFixture.testQuickFixAll("AssertArrayEquals.kt")
  }

  fun `test AssertArrayEquals message`() {
    myFixture.testQuickFixAll("AssertArrayEqualsMessage.kt")
  }

  fun `test AssertEquals`() {
    myFixture.testQuickFixAll("AssertEquals.kt")
  }

  fun `test AssertNotEqualsWithDelta`() {
    myFixture.testQuickFixUnavailableAll("AssertNotEqualsWithDelta.kt")
  }

  fun `test AssertThat`() {
    myFixture.testQuickFixAll("AssertThat.kt")
  }

  fun `test AssertTrue`() {
    myFixture.testQuickFixAll("AssertTrue.kt")
  }

  fun `test AssertTrue method reference`() {
    myFixture.testQuickFixAll("AssertTrueMethodRef.kt")
  }

  fun `test AssumeTrue`() {
    myFixture.testQuickFixAll("AssumeTrue.kt")
  }
}