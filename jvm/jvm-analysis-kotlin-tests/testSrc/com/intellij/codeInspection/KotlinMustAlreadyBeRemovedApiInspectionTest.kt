package com.intellij.codeInspection

import com.intellij.codeInspection.tests.MustAlreadyBeRemovedApiInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil
import com.intellij.testFramework.TestDataPath

@TestDataPath("\$CONTENT_ROOT/testData/codeInspection/mustAlreadyBeRemovedApi")
class KotlinMustAlreadyBeRemovedApiInspectionTest : MustAlreadyBeRemovedApiInspectionTestBase() {

  override fun getBasePath() = JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/mustAlreadyBeRemovedApi"

  fun `test APIs must have been removed`() {
    myFixture.testHighlighting("outdatedApi.kt")
  }
}