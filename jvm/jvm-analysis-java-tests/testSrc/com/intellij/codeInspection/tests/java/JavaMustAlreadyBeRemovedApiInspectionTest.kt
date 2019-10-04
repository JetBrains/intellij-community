package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.MustAlreadyBeRemovedApiInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisTestsUtil
import com.intellij.testFramework.TestDataPath

@TestDataPath("\$CONTENT_ROOT/testData/codeInspection/mustAlreadyBeRemovedApi")
class JavaMustAlreadyBeRemovedApiInspectionTest : MustAlreadyBeRemovedApiInspectionTestBase() {

  override fun getTestDataPath() = JvmAnalysisTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/mustAlreadyBeRemovedApi"

  fun `test APIs must have been removed`() {
    myFixture.testHighlighting("outdatedApi.java")
  }
}