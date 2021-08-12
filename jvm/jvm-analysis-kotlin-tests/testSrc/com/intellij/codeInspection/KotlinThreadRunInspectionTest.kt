package com.intellij.codeInspection

import com.intellij.codeInspection.tests.ThreadRunInspectionTestBase
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/threadrun"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinThreadRunInspectionTest : ThreadRunInspectionTestBase() {
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  override val fileExt: String = "kt"

  fun `test highlighting`() = testHighlighting("ThreadRunTest")

  fun `test no highlighting super`() = testHighlighting("ThreadRunSuperTest")

  fun `test quickfix`() = testQuickFixAll("ThreadRunQfTest")
}