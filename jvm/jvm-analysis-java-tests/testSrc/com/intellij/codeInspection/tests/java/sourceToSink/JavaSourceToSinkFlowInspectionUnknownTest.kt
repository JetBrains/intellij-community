package com.intellij.codeInspection.tests.java.sourceToSink

import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.sourceToSink.sourceToSinkFlowInspectionFixture
import com.intellij.jvm.analysis.internal.testFramework.sourceToSink.sourceToSinkFlowProjectFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

private const val INSPECTION_PATH = "/codeInspection/sourceToSinkFlow/unknown"

@TestDataPath("\$CONTENT_ROOT/testData$INSPECTION_PATH")
@TestApplication
class JavaSourceToSinkFlowInspectionUnknownTest {
  companion object {
    private val projectFixture = sourceToSinkFlowProjectFixture()
  }

  private val myFixture by sourceToSinkFlowInspectionFixture(projectFixture, JavaJvmAnalysisTestUtil.testDataPath + INSPECTION_PATH) {
    showUnknownObject = false
  }

  @Test
  fun testSimple() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("Simple.java")
  }
}