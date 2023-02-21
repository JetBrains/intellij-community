package com.intellij.codeInspection.tests.kotlin.test

import com.intellij.codeInspection.tests.test.AssertEqualsBetweenInconvertibleTypesInspectionTestBase
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/assertEqualsBetweenInconvertibleTypes"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinAssertEqualsBetweenInconvertibleTypesTest : AssertEqualsBetweenInconvertibleTypesInspectionTestBase() {
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  fun `test AssertEqualsBetweenInconvertibleTypes`() {
    myFixture.testHighlighting("AssertEqualsBetweenInconvertibleTypes.kt")
  }
}