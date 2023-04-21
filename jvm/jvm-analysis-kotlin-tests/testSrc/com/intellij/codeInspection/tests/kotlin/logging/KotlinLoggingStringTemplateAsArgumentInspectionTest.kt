package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.codeInspection.tests.logging.LoggingStringTemplateAsArgumentInspectionTestBase
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val INSPECTION_PATH = "/codeInspection/logging/stringTemplateAsArgument"

@TestDataPath("\$CONTENT_ROOT/testData$INSPECTION_PATH")
class KotlinLoggingStringTemplateAsArgumentInspectionTest : LoggingStringTemplateAsArgumentInspectionTestBase() {

  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + INSPECTION_PATH
  fun `test highlighting, myLimitLevelType=2 mySkipPrimitives=false`() {
    myFixture.testHighlighting("StringTemplateAsArgumentWarnInfo.kt")
  }

  fun `test highlighting, myLimitLevelType=3 mySkipPrimitives=false`() {
    myFixture.testHighlighting("StringTemplateAsArgumentWarnDebug.kt")
  }

  fun `test highlighting, myLimitLevelType=0 mySkipPrimitives=true`() {
    myFixture.testHighlighting("StringTemplateAsArgumentSkipPrimitives.kt")
  }

  fun `test highlighting, myLimitLevelType=0 mySkipPrimitives=false`() {
    myFixture.testHighlighting("StringTemplateAsArgument.kt")
  }

  fun `test fix, myLimitLevelType=0 mySkipPrimitives=false`() {
    myFixture.testQuickFix(file = "StringTemplateAsArgumentFix.kt", checkPreview = true)
  }

  fun `test highlighting, with guards`() {
    myFixture.testHighlighting("StringTemplateAsArgumentGuarded.kt")
  }
}