package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.emptyMethod.EmptyMethodInspection
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

private const val inspectionPath = "/codeInspection/emptyMethod"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
abstract class KotlinEmptyMethodInspectionTest : JvmInspectionTestBase(), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

  override var inspection = EmptyMethodInspection()

  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  fun `test basic`() {
    myFixture.testInspection("basic", GlobalInspectionToolWrapper(inspection))
  }
}