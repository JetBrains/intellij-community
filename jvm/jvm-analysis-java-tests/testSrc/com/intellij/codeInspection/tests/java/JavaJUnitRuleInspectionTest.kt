package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.JUnitRuleInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisTestsUtil

class JavaJUnitRuleInspectionTest : JUnitRuleInspectionTestBase() {
  override val fileExt: String = "java"

  override fun getTestDataPath() = JvmAnalysisTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/junitrule"

  fun `test @Rule highlighting`() = testHighlighting("RuleTest")

  fun `test @Rule quickFixes`() {
    val quickfixes = myFixture.getAllQuickFixes("RuleQfTest.java")
    quickfixes.forEach { myFixture.launchAction(it) }
    myFixture.checkResultByFile("RuleQfTest.after.java")
  }

  fun `test @ClassRule highlighting`() = testHighlighting("ClassRuleTest")

  fun `test @ClassRule quickFixes`() {
    val quickfixes = myFixture.getAllQuickFixes("ClassRuleQfTest.java")
    quickfixes.forEach { myFixture.launchAction(it) }
    myFixture.checkResultByFile("ClassRuleQfTest.after.java")
  }
}