package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.JUnitRuleInspectionTestBase
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/junitrule"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinJUnitRuleInspectionTest : JUnitRuleInspectionTestBase() {
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

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