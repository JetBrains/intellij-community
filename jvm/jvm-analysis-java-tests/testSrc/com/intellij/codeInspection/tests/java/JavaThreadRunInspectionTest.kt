package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.ThreadRunInspectionTestBase
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/threadrun"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class JavaThreadRunInspectionTest : ThreadRunInspectionTestBase() {
  override fun getTestDataPath(): String = JavaJvmAnalysisTestUtil.testDataPath + inspectionPath

  fun `test highlighting`() {
    myFixture.testHighlighting("ThreadRunTest.java")
  }

  fun `test no highlighting super`() {
    myFixture.testHighlighting("ThreadRunSuperTest.java")
  }

  fun `test quickfix`() {
    myFixture.testQuickFix("ThreadRunQfTest.java")
  }
}