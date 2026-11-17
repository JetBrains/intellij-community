package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.jvmSdkProjectFixture
import com.intellij.jvm.analysis.internal.testFramework.threadRunInspectionFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

private const val inspectionPath = "/codeInspection/threadrun"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
@TestApplication
class JavaThreadRunInspectionTest {
  companion object {
    private val projectFixture = jvmSdkProjectFixture()
  }

  private val myFixture by threadRunInspectionFixture(projectFixture, JavaJvmAnalysisTestUtil.testDataPath + inspectionPath)

  @Test
  fun `test highlighting`() {
    myFixture.testHighlighting("ThreadRunTest.java")
  }

  @Test
  fun `test no highlighting super`() {
    myFixture.testHighlighting("ThreadRunSuperTest.java")
  }

  @Test
  fun `test quickfix`() {
    myFixture.testQuickFix("ThreadRunQfTest.java")
  }
}