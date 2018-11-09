package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.ScheduledForRemovalInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH
import com.intellij.testFramework.TestDataPath

@TestDataPath("/testData/codeInspection/scheduledForRemoval")
class ScheduledForRemovalInspectionTest: ScheduledForRemovalInspectionTestBase() {
  override fun getBasePath() = ""

  override fun getTestDataPath() = "${TEST_DATA_PROJECT_RELATIVE_BASE_PATH}/codeInspection/scheduledForRemoval"

  fun testInspection() {
    getInspection().myIgnoreInsideImports = false
    myFixture.testHighlighting(true, false, false, "ScheduledForRemovalElementsTest.java")
  }

  fun testIgnoreImports() {
    getInspection().myIgnoreInsideImports = true
    myFixture.testHighlighting(true, false, false, "ScheduledForRemovalElementsIgnoreImportsTest.java")
  }
}