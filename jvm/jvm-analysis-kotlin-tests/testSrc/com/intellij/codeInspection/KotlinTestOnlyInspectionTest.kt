package com.intellij.codeInspection

import com.intellij.codeInspection.tests.TestOnlyInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil

class KotlinTestOnlyInspectionTest : TestOnlyInspectionTestBase() {
  override val fileExt: String = "kt"

  override fun getBasePath() = JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/testonly"

  fun `test @TestOnly on use-site targets`() = testHighlighting("UseSiteTargets")

  fun `test @TestOnly in production code`() = testHighlighting("TestOnlyTest")

  fun `test @VisibleForTesting in production code`() = testHighlighting("VisibleForTestingTest", "VisibleForTestingTestApi")
}