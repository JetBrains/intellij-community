// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.JUnit5AssertionsConverterInspectionTestBase
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/junit5assertionsconverter"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class JavaJUnit5AssertionsConverterInspectionTest : JUnit5AssertionsConverterInspectionTestBase() {
  override fun getBasePath() = JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  fun `test AssertArrayEquals`() {
    myFixture.testQuickFixAll("AssertArrayEquals.java")
  }

  fun `test AssertArrayEquals message`() {
    myFixture.testQuickFixAll("AssertArrayEqualsMessage.java")
  }

  fun `test AssertEquals`() {
    myFixture.testQuickFixAll("AssertEquals.java")
  }

  fun `test AssertNotEqualsWithDelta`() {
    myFixture.testQuickFixUnavailableAll("AssertNotEqualsWithDelta.java")
  }

  fun `test AssertThat`() {
    myFixture.testQuickFixAll("AssertThat.java")
  }

  fun `test AssertTrue`() {
    myFixture.testQuickFixAll("AssertTrue.java")
  }

  fun `test AssertTrue method reference`() {
    myFixture.testQuickFixAll("AssertTrueMethodRef.java")
  }

  fun `test AssumeTrue`() {
    myFixture.testQuickFixAll("AssumeTrue.java")
  }
}