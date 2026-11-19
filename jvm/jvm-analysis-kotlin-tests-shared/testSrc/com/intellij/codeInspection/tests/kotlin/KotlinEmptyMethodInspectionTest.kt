package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.emptyMethod.EmptyMethodInspection
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.jvm.analysis.testFramework.jvmInspectionFixture
import com.intellij.jvm.analysis.testFramework.jvmProjectFixture
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

private const val inspectionPath = "/codeInspection/emptyMethod"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
@TestApplication
abstract class KotlinEmptyMethodInspectionTest {
  companion object {
    private val projectFixture = jvmProjectFixture()
  }

  protected val myFixture by jvmInspectionFixture(projectFixture, testDataPath = KotlinJvmAnalysisTestUtil.testDataPath + inspectionPath) { EmptyMethodInspection() }

  @Test
  fun `test basic`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      myFixture.testInspection("basic", GlobalInspectionToolWrapper(myFixture.inspection))
    }
  }

  @Test
  fun `test accessors`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      myFixture.testInspection("accessor", GlobalInspectionToolWrapper(myFixture.inspection))
    }
  }
}