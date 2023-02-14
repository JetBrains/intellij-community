package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.AssertEqualsBetweenInconvertibleTypesInspectionTestBase
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
