package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.TestOnlyInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisTestsUtil

class JavaTestOnlyInspectionTest : TestOnlyInspectionTestBase() {
  override fun getTestDataPath() = JvmAnalysisTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/testonly"

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