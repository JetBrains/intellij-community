package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.MustAlreadyBeRemovedApiInspectionTestBase
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/mustAlreadyBeRemovedApi"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class JavaMustAlreadyBeRemovedApiInspectionTest : MustAlreadyBeRemovedApiInspectionTestBase() {
  override fun getBasePath() = JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  fun `test APIs must have been removed`() {
    myFixture.testHighlighting("outdatedApi.java")
  }
}