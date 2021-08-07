package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.TestFailedLineInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisTestsUtil

class JavaTestFailedLineInspectionTest : TestFailedLineInspectionTestBase() {
  override fun getTestDataPath() = JvmAnalysisTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/testfailedline"

  fun testMainTest() {
    doTest(
      fileName = "MainTest",
      fileExt = "java",
      methodName = "testFoo",
      errorLn = 3,
      errorMessage = "junit.framework.AssertionFailedError:"
    )
  }

  fun testQualifiedTest() {
    doTest(
      fileName = "QualifiedTest",
      fileExt = "java",
      methodName = "testFoo",
      errorLn = 3,
      errorMessage = "junit.framework.AssertionFailedError:"
    )
  }
}