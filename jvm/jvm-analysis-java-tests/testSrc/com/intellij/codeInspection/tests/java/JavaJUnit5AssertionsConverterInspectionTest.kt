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
    myFixture.testQuickFix("AssertArrayEquals.java")
  }

  fun `test AssertArrayEquals message`() {
    myFixture.testQuickFix("AssertArrayEqualsMessage.java")
  }

  fun `test AssertEquals`() {
    myFixture.testQuickFix("AssertEquals.java")
  }

  fun `test AssertNotEqualsWithDelta`() {
    myFixture.testQuickFixUnavailable("AssertNotEqualsWithDelta.java")
  }

  fun `test AssertThat`() {
    myFixture.testQuickFix("AssertThat.java")
  }

  fun `test AssertTrue`() {
    myFixture.testQuickFix("AssertTrue.java")
  }

  fun `test AssertTrue method reference`() {
    myFixture.testQuickFix("AssertTrueMethodRef.java")
  }

  fun `test AssumeTrue`() {
    myFixture.testQuickFix("AssumeTrue.java")
  }
}