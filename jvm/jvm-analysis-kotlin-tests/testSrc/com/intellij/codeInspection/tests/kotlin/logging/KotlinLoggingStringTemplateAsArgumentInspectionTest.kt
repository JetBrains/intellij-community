package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.codeInspection.tests.logging.LoggingStringTemplateAsArgumentInspectionTestBase
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val INSPECTION_PATH = "/codeInspection/logging/stringTemplateAsArgument"

@TestDataPath("\$CONTENT_ROOT/testData$INSPECTION_PATH")
class KotlinLoggingStringTemplateAsArgumentInspectionTest : LoggingStringTemplateAsArgumentInspectionTestBase() {

  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + INSPECTION_PATH
  fun `test highlighting`() {
    myFixture.testHighlighting("StringTemplateAsArgument.kt")
  }

  fun `test fix`() {
    myFixture.testQuickFix(file = "StringTemplateAsArgumentFix.kt", checkPreview = true)
  }
}