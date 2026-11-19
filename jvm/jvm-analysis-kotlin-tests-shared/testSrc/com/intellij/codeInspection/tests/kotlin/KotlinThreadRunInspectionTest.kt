package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.jvmSdkProjectFixture
import com.intellij.jvm.analysis.internal.testFramework.threadRunInspectionFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

private const val inspectionPath = "/codeInspection/threadrun"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
@TestApplication
abstract class KotlinThreadRunInspectionTest {
  companion object {
    private val projectFixture = jvmSdkProjectFixture()
  }

  protected val myFixture by threadRunInspectionFixture(projectFixture, KotlinJvmAnalysisTestUtil.testDataPath + inspectionPath)

  @Test
  fun `test highlighting`() {
    myFixture.testHighlighting("ThreadRunTest.kt")
  }

  @Test
  fun `test no highlighting super`() {
    myFixture.testHighlighting("ThreadRunSuperTest.kt")
  }

  @Test
  fun `test quickfix`() {
    myFixture.testQuickFix("ThreadRunQfTest.kt")
  }
}