package com.intellij.codeInspection

import com.intellij.codeInspection.tests.TestOnlyInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil

class KotlinTestOnlyInspectionTest : TestOnlyInspectionTestBase() {
  override fun getBasePath() = JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/testonly"

  fun `test @TestOnly in production code`() {
    myFixture.testHighlighting("TestOnlyTest.kt")
  }

  fun `test @VisibleForTesting in production code`() {
    myFixture.testHighlighting("VisibleForTestingTest.kt", "VisibleForTestingTestApi.kt")
  }
}