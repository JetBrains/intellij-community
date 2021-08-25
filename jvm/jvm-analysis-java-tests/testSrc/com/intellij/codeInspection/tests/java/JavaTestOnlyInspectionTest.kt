package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.TestOnlyInspectionTestBase
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/testonly"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class JavaTestOnlyInspectionTest : TestOnlyInspectionTestBase() {
  override fun getBasePath() = JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  fun `test @TestOnly not highlighting in javadoc`() {
    myFixture.testHighlighting("TestOnlyDoc.java")
  }

  fun `test @TestOnly in production code`() {
    myFixture.testHighlighting("TestOnlyTest.java")
  }

  fun `test @VisibleForTesting in production code`() {
    myFixture.testHighlighting("VisibleForTestingTest.java", "VisibleForTestingTestApi.java")
  }
}