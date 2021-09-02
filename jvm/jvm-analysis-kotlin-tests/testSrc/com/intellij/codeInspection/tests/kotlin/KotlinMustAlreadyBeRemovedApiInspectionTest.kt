package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.MustAlreadyBeRemovedApiInspectionTestBase
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/mustAlreadyBeRemovedApi"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinMustAlreadyBeRemovedApiInspectionTest : MustAlreadyBeRemovedApiInspectionTestBase() {
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  fun `test APIs must have been removed`() {
    myFixture.testHighlighting("outdatedApi.kt")
  }
}