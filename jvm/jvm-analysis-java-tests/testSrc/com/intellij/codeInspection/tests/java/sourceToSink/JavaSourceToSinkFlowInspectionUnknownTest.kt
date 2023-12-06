package com.intellij.codeInspection.tests.java.sourceToSink

import com.intellij.codeInspection.sourceToSink.SourceToSinkFlowInspection
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.sourceToSink.SourceToSinkFlowInspectionTestBase
import com.intellij.testFramework.TestDataPath

private const val INSPECTION_PATH = "/codeInspection/sourceToSinkFlow/unknown"

@TestDataPath("\$CONTENT_ROOT/testData$INSPECTION_PATH")
class JavaSourceToSinkFlowInspectionUnknownTest : SourceToSinkFlowInspectionTestBase() {

  override val inspection: SourceToSinkFlowInspection
    get() = super.inspection.also {
      it.showUnknownObject = false
    }

  override fun getBasePath(): String {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + INSPECTION_PATH
  }

  fun testSimple() {
    prepareCheckFramework()
    myFixture.testHighlighting("Simple.java")
  }
}