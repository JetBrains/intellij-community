package com.intellij.codeInspection

import com.intellij.codeInspection.tests.JUnitRuleInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil

class KotlinJUnitRuleInspectionTest : JUnitRuleInspectionTestBase() {
  override fun getBasePath() = JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/junitrule"

  fun `test @Rule highlighting`() {
    myFixture.testHighlighting("Rule.kt")
  }

  fun `test @Rule quickFixes`() {
    val quickfixes = myFixture.getAllQuickFixes("RuleQf.kt")
    quickfixes.forEach { myFixture.launchAction(it) }
    myFixture.checkResultByFile("RuleQf.after.kt")
  }

  fun `test @ClassRule highlighting`() {
    myFixture.testHighlighting("ClassRule.kt")
  }

  fun `test @ClassRule quickFixes`() {
    val quickfixes = myFixture.getAllQuickFixes("ClassRuleQf.kt")
    quickfixes.forEach { myFixture.launchAction(it) }
    myFixture.checkResultByFile("ClassRuleQf.after.kt")
  }
}