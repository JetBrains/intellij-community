package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.logging.LoggingStringTemplateAsArgumentInspection
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingStringTemplateAsArgumentInspectionTestBase
import com.intellij.testFramework.TestDataPath
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

private const val INSPECTION_PATH = "/codeInspection/logging/stringTemplateAsArgument"

@RunWith(Enclosed::class)
class KotlinLoggingStringTemplateAsArgumentInspectionTest {

  @TestDataPath("\$CONTENT_ROOT/testData$INSPECTION_PATH")
  abstract class KotlinLoggingStringTemplateAsArgumentInspectionTestBase : LoggingStringTemplateAsArgumentInspectionTestBase() {
    override val inspection: InspectionProfileEntry
      get() {
        val loggingStringTemplateAsArgumentInspection = LoggingStringTemplateAsArgumentInspection()
        loggingStringTemplateAsArgumentInspection.myLimitLevelType = LoggingStringTemplateAsArgumentInspection.LimitLevelType.ALL
        return loggingStringTemplateAsArgumentInspection
      }

    override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + INSPECTION_PATH
  }
  class TestCaseByDefault : KotlinLoggingStringTemplateAsArgumentInspectionTestBase() {
    fun `test highlighting, with guards`() {
      myFixture.testHighlighting("StringTemplateAsArgumentGuarded.kt")
    }
  }


  class TestCaseMyLimitLevelTypeInfoMySkipPrimitivesFalse : KotlinLoggingStringTemplateAsArgumentInspectionTestBase() {
    override val inspection: InspectionProfileEntry
      get() = LoggingStringTemplateAsArgumentInspection().apply {
        mySkipPrimitives = false
        myLimitLevelType = LoggingStringTemplateAsArgumentInspection.LimitLevelType.INFO_AND_LOWER
      }

    fun `test highlighting`() {
      myFixture.testHighlighting("StringTemplateAsArgumentWarnInfo.kt")
    }
  }

  class TestCaseMyLimitLevelTypeDebugMySkipPrimitivesFalse : KotlinLoggingStringTemplateAsArgumentInspectionTestBase() {

    override val inspection: InspectionProfileEntry
      get() = LoggingStringTemplateAsArgumentInspection().apply {
        mySkipPrimitives = false
        myLimitLevelType = LoggingStringTemplateAsArgumentInspection.LimitLevelType.DEBUG_AND_LOWER
      }

    fun `test highlighting`() {
      myFixture.testHighlighting("StringTemplateAsArgumentWarnDebug.kt")
    }
  }

  class TestCaseMyLimitLevelTypeAllMySkipPrimitivesTrue : KotlinLoggingStringTemplateAsArgumentInspectionTestBase() {
    override val inspection: InspectionProfileEntry
      get() = LoggingStringTemplateAsArgumentInspection().apply {
        mySkipPrimitives = true
        myLimitLevelType = LoggingStringTemplateAsArgumentInspection.LimitLevelType.ALL
      }

    fun `test highlighting`() {
      myFixture.testHighlighting("StringTemplateAsArgumentSkipPrimitives.kt")
    }
  }

  class TestCaseMyLimitLevelTypeAllMySkipPrimitivesFalse : KotlinLoggingStringTemplateAsArgumentInspectionTestBase() {
    override val inspection: InspectionProfileEntry
      get() = LoggingStringTemplateAsArgumentInspection().apply {
        mySkipPrimitives = false
        myLimitLevelType = LoggingStringTemplateAsArgumentInspection.LimitLevelType.ALL
      }

    fun `test highlighting`() {
      myFixture.testHighlighting("StringTemplateAsArgument.kt")
    }

    fun `test fix`() {
      myFixture.testQuickFix(file = "StringTemplateAsArgumentFix.kt", checkPreview = true)
    }
  }
}