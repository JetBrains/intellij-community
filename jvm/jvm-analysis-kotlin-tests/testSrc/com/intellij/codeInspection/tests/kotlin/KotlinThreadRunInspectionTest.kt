package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.ThreadRunInspectionTestBase
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/threadrun"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinThreadRunInspectionTest : ThreadRunInspectionTestBase() {
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  fun `test highlighting`() {
    myFixture.testHighlighting("ThreadRunTest.kt")
  }

  fun `test no highlighting super`() {
    myFixture.testHighlighting("ThreadRunSuperTest.kt")
  }

  fun `test quickfix`() {
    myFixture.testQuickFix("ThreadRunQfTest.kt")
  }
}