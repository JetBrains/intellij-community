package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.TestOnlyInspectionTestBase
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/testonly"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinTestOnlyInspectionTest : TestOnlyInspectionTestBase() {
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  fun `test @TestOnly on use-site targets`() {
    myFixture.testHighlighting("UseSiteTargets.kt")
  }

  fun `test @TestOnly in production code`() {
    myFixture.testHighlighting("TestOnlyTest.kt")
  }

  fun `test @VisibleForTesting in production code`() {
    myFixture.testHighlighting("VisibleForTestingTest.kt", "VisibleForTestingTestApi.kt")
  }
}