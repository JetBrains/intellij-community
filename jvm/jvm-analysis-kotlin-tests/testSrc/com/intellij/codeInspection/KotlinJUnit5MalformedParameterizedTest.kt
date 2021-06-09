package com.intellij.codeInspection

import com.intellij.codeInspection.tests.JUnitMalformedInspectionTestBaseKt
import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil
import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil

class KotlinJUnit5MalformedParameterizedUsageTestKt : JUnitMalformedInspectionTestBaseKt() {

  override fun setUp() {
    super.setUp()
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture)
  }

  override fun getBasePath() = JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/junit5malformed"

  fun `test CantResolveTarget`() {
    myFixture.testHighlighting("CantResolveTarget.kt")
  }

  fun `test CantResolveTarget highlighting`() {
    myFixture.testHighlighting("CantResolveTarget.kt")
  }

  fun `test StaticMethodSourceTest quickFixes`() {
    val quickfixes = myFixture.getAllQuickFixes("StaticMethodSource.kt")
    quickfixes.forEach { myFixture.launchAction(it) }
    myFixture.checkResultByFile("StaticMethodSource.after.kt")
  }

  fun `test SuspiciousCombination quickFixes`() {
    myFixture.testHighlighting("SuspiciousCombination.kt")
  }

  fun `test NoSourcesProvided quickFixes`() {
    myFixture.testHighlighting("NoSourcesProvided.kt")
  }

  fun `test ExactlyOneType quickFixes`() {
    myFixture.testHighlighting("ExactlyOneType.kt")
  }

  fun `test NoParams quickFixes`() {
    myFixture.testHighlighting("NoParams.kt")
  }

  fun `test ReturnType quickFixes`() {
    myFixture.testHighlighting("ReturnType.kt")
  }

  fun `test EnumResolve quickFixes`() {
    myFixture.testHighlighting("EnumResolve.kt")
  }
}