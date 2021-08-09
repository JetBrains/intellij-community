package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.TestOnlyInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisTestsUtil

class JavaTestOnlyInspectionTest : TestOnlyInspectionTestBase() {
  override val fileExt: String = "java"

  override fun getTestDataPath() = JvmAnalysisTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/testonly"

  fun `test @TestOnly not highlighting in javadoc`() = testHighlighting("TestOnlyDoc")

  fun `test @TestOnly in production code`() = testHighlighting("TestOnlyTest")

  fun `test @VisibleForTesting in production code`() = testHighlighting("VisibleForTestingTest", "VisibleForTestingTestApi")
}