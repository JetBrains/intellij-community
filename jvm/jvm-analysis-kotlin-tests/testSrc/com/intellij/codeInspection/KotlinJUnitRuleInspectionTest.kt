package com.intellij.codeInspection

import com.intellij.codeInspection.tests.JUnitRuleInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil

class KotlinJUnitRuleInspectionTest : JUnitRuleInspectionTestBase() {
  override fun getBasePath() = JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/junitrule"

  fun `test @Rule`() {
    myFixture.testHighlighting("Rule.kt")
  }

  fun `test @ClassRule`() {
    myFixture.testHighlighting("ClassRule.kt")
  }
}