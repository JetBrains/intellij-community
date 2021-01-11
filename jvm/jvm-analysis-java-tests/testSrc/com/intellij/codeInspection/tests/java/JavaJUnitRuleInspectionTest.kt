package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.JUnitRuleInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisTestsUtil

class JavaJUnitRuleInspectionTest : JUnitRuleInspectionTestBase() {
  override fun getTestDataPath() = JvmAnalysisTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/junitrule"

  fun `test @Rule`() {
    myFixture.testHighlighting("RuleTest.java")
  }

  fun `test @ClassRule`() {
    myFixture.testHighlighting("ClassRuleTest.java")
  }
}