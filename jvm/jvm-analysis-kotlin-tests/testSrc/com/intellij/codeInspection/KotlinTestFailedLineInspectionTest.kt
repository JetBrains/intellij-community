package com.intellij.codeInspection

import com.intellij.codeInspection.tests.TestFailedLineInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil

class KotlinTestFailedLineInspectionTest : TestFailedLineInspectionTestBase() {
  override fun getBasePath() = JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/testfailedline"

  fun testMainTest() {
    doTest(
      fileName = "MainTest",
      fileExt = "kt",
      methodName = "testFoo",
      errorLn = 3,
      errorMessage = "junit.framework.AssertionFailedError:"
    )
  }

  fun testQualifiedTest() {
    doTest(
      fileName = "QualifiedTest",
      fileExt = "kt",
      methodName = "testFoo",
      errorLn = 3,
      errorMessage = "junit.framework.AssertionFailedError:"
    )
  }
}