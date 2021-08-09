package com.intellij.codeInspection

import com.intellij.codeInspection.tests.TestFailedLineInspectionTestBase
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/testfailedline"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinTestFailedLineInspectionTest : TestFailedLineInspectionTestBase() {
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  override val fileExt: String = "kt"

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