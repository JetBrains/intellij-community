package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.codeInspection.logging.LoggingStringTemplateAsArgumentInspection
import com.intellij.codeInspection.logging.LoggingUtil
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingStringTemplateAsArgumentInspectionTestBase
import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

private const val INSPECTION_PATH = "/codeInspection/logging/stringTemplateAsArgument"

@TestDataPath("\$CONTENT_ROOT/testData$INSPECTION_PATH")
abstract class KotlinLoggingStringTemplateAsArgumentInspectionTest : LoggingStringTemplateAsArgumentInspectionTestBase(), KotlinPluginModeProvider {
  override val inspection: LoggingStringTemplateAsArgumentInspection = LoggingStringTemplateAsArgumentInspection().apply {
    myLimitLevelType = LoggingUtil.LimitLevelType.ALL
  }

  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + INSPECTION_PATH

  private fun withSettings(
    skipPrimitives: Boolean = inspection.mySkipPrimitives,
    levelType: LoggingUtil.LimitLevelType = inspection.myLimitLevelType,
    skipWithTheOnlyException: Boolean = inspection.mySkipWithTheOnlyException,
    test: () -> Unit
  ) {
    val curSkipPrimitives = inspection.mySkipPrimitives
    val curLimitLevelType = inspection.myLimitLevelType
    val curSkipWithTheOnlyException = inspection.mySkipWithTheOnlyException
    try {
      inspection.mySkipPrimitives = skipPrimitives
      inspection.myLimitLevelType = levelType
      inspection.mySkipWithTheOnlyException = skipWithTheOnlyException
      test()
    } finally {
      inspection.mySkipPrimitives = curSkipPrimitives
      inspection.myLimitLevelType = curLimitLevelType
      inspection.mySkipWithTheOnlyException = curSkipWithTheOnlyException
    }
  }

  fun `test default settings highlighting, with guards`() {
    myFixture.testHighlighting("StringTemplateAsArgumentGuarded.kt")
  }

  fun `test highlighting info and lower no skip primitives`() {
    withSettings(skipPrimitives = false, levelType = LoggingUtil.LimitLevelType.INFO_AND_LOWER) {
      myFixture.testHighlighting("StringTemplateAsArgumentWarnInfo.kt")
    }
  }

  fun `test highlighting debug no skip primitives`() {
    withSettings(skipPrimitives = false, LoggingUtil.LimitLevelType.DEBUG_AND_LOWER) {
      myFixture.testHighlighting("StringTemplateAsArgumentWarnDebug.kt")
    }
  }

  fun `test highlighting all skip primitives`() {
    withSettings(skipPrimitives = true, LoggingUtil.LimitLevelType.ALL) {
      myFixture.testHighlighting("StringTemplateAsArgumentSkipPrimitives.kt")
    }
  }

  fun `test highlighting all no skip primitives`() {
     withSettings(skipPrimitives = false, LoggingUtil.LimitLevelType.ALL) {
      myFixture.testHighlighting("StringTemplateAsArgument.kt")
    }
  }

  fun `test fix all no skip primitives`() {
    withSettings(skipPrimitives = false, LoggingUtil.LimitLevelType.ALL) {
      myFixture.testQuickFix("StringTemplateAsArgumentFix.kt", checkPreview = true)
    }
  }

  fun `test fix with escape symbol all no skip primitives`() {
    withSettings(skipPrimitives = false, LoggingUtil.LimitLevelType.ALL) {
      myFixture.testQuickFix("StringTemplateAsArgumentWithEscapeSymbolsFix.kt", checkPreview = true)
    }
  }

  fun `test highlighting all no skip primitives skip with the only exception`() {
    withSettings(skipPrimitives = false, LoggingUtil.LimitLevelType.ALL, skipWithTheOnlyException = true) {
      myFixture.testHighlighting("StringTemplateAsArgumentSkipException.kt")
    }
  }
}