package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.ScheduledForRemovalInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl

@TestDataPath("/testData/codeInspection/scheduledForRemoval")
class KtScheduledForRemovalInspectionTest : ScheduledForRemovalInspectionTestBase() {
  override fun getBasePath() = "${TEST_DATA_PROJECT_RELATIVE_BASE_PATH}/codeInspection/scheduledForRemoval"

  override fun performAdditionalSetUp() {
    // otherwise assertion in PsiFileImpl ("Access to tree elements not allowed") will not pass
    (myFixture as CodeInsightTestFixtureImpl).setVirtualFileFilter(VirtualFileFilter.NONE)
  }

  fun testInspection() {
    getInspection().myIgnoreInsideImports = false
    myFixture.testHighlighting("ScheduledForRemovalElementsTest.kt")
  }

  fun testIgnoreImports() {
    getInspection().myIgnoreInsideImports = true
    myFixture.testHighlighting("ScheduledForRemovalElementsIgnoreImportsTest.kt")
  }
}