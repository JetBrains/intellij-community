package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.codeInspection.logging.LoggingStringTemplateAsArgumentInspection
import com.intellij.codeInspection.logging.LoggingUtil
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.logging.loggingInspectionFixture
import com.intellij.jvm.analysis.testFramework.jvmProjectFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

private const val INSPECTION_PATH = "/codeInspection/logging/stringTemplateAsArgument"

@TestDataPath("\$CONTENT_ROOT/testData$INSPECTION_PATH")
@TestApplication
abstract class KotlinLoggingStringTemplateAsArgumentInspectionTest {
  companion object {
    private val projectFixture = jvmProjectFixture()
  }

  protected val myFixture by loggingInspectionFixture(projectFixture, testDataPath = KotlinJvmAnalysisTestUtil.testDataPath + INSPECTION_PATH) {
    LoggingStringTemplateAsArgumentInspection().apply {
      myLimitLevelType = LoggingUtil.LimitLevelType.ALL
    }
  }

  private fun withSettings(
    skipPrimitives: Boolean = myFixture.inspection.mySkipPrimitives,
    levelType: LoggingUtil.LimitLevelType = myFixture.inspection.myLimitLevelType,
    skipWithTheOnlyException: Boolean = myFixture.inspection.mySkipWithTheOnlyException,
    test: () -> Unit
  ) {
    val curSkipPrimitives = myFixture.inspection.mySkipPrimitives
    val curLimitLevelType = myFixture.inspection.myLimitLevelType
    val curSkipWithTheOnlyException = myFixture.inspection.mySkipWithTheOnlyException
    try {
      myFixture.inspection.mySkipPrimitives = skipPrimitives
      myFixture.inspection.myLimitLevelType = levelType
      myFixture.inspection.mySkipWithTheOnlyException = skipWithTheOnlyException
      test()
    } finally {
      myFixture.inspection.mySkipPrimitives = curSkipPrimitives
      myFixture.inspection.myLimitLevelType = curLimitLevelType
      myFixture.inspection.mySkipWithTheOnlyException = curSkipWithTheOnlyException
    }
  }

  @Test
  fun `test default settings highlighting, with guards`() {
    myFixture.testHighlighting("StringTemplateAsArgumentGuarded.kt")
  }

  @Test
  fun `test highlighting info and lower no skip primitives`() {
    withSettings(skipPrimitives = false, levelType = LoggingUtil.LimitLevelType.INFO_AND_LOWER) {
      myFixture.testHighlighting("StringTemplateAsArgumentWarnInfo.kt")
    }
  }

  @Test
  fun `test highlighting debug no skip primitives`() {
    withSettings(skipPrimitives = false, LoggingUtil.LimitLevelType.DEBUG_AND_LOWER) {
      myFixture.testHighlighting("StringTemplateAsArgumentWarnDebug.kt")
    }
  }

  @Test
  fun `test highlighting all skip primitives`() {
    withSettings(skipPrimitives = true, LoggingUtil.LimitLevelType.ALL) {
      myFixture.testHighlighting("StringTemplateAsArgumentSkipPrimitives.kt")
    }
  }

  @Test
  fun `test highlighting all no skip primitives`() {
     withSettings(skipPrimitives = false, LoggingUtil.LimitLevelType.ALL) {
      myFixture.testHighlighting("StringTemplateAsArgument.kt")
    }
  }

  @Test
  fun `test fix all no skip primitives`() {
    withSettings(skipPrimitives = false, LoggingUtil.LimitLevelType.ALL) {
      myFixture.testQuickFix("StringTemplateAsArgumentFix.kt", checkPreview = true)
    }
  }

  @Test
  fun `test fix with escape symbol all no skip primitives`() {
    withSettings(skipPrimitives = false, LoggingUtil.LimitLevelType.ALL) {
      myFixture.testQuickFix("StringTemplateAsArgumentWithEscapeSymbolsFix.kt", checkPreview = true)
    }
  }

  @Test
  fun `test highlighting all no skip primitives skip with the only exception`() {
    withSettings(skipPrimitives = false, LoggingUtil.LimitLevelType.ALL, skipWithTheOnlyException = true) {
      myFixture.testHighlighting("StringTemplateAsArgumentSkipException.kt")
    }
  }
}