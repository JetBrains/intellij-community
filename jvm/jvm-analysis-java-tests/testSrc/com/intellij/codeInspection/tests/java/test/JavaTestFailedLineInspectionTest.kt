package com.intellij.codeInspection.tests.java.test

import com.intellij.codeInspection.tests.test.TestFailedLineInspectionTestBase
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/testfailedline"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class JavaTestFailedLineInspectionTest : TestFailedLineInspectionTestBase() {
  override fun getBasePath() = JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

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