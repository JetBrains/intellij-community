package com.intellij.codeInspection.tests.kotlin.sourceToSink

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.sourceToSink.sourceToSinkFlowInspectionFixture
import com.intellij.jvm.analysis.internal.testFramework.sourceToSink.sourceToSinkFlowProjectFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.annotations.Nls
import org.junit.jupiter.api.Test

private const val inspectionPath = "/codeInspection/sourceToSinkFlow/markAsSafeFix"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
@TestApplication
abstract class KotlinMarkAsSafeFixSourceToSinkFlowInspectionTest {
  companion object {
    private val projectFixture = sourceToSinkFlowProjectFixture()
  }

  protected val myFixture by sourceToSinkFlowInspectionFixture(projectFixture, KotlinJvmAnalysisTestUtil.testDataPath + inspectionPath)

  @Test
  fun `test common cases with checkFramework`() {
    myFixture.prepareCheckFramework()
    myFixture.testQuickFix("CommonCasesCheckFramework.kt", getMessage(), false)
  }

  @Test
  fun `test common cases with jsr`() {
    myFixture.prepareJsr()
    myFixture.testQuickFix("CommonCasesJsr.kt", getMessage(), false)
  }

  private fun getMessage(): @Nls String = JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.mark.as.safe.text")
}