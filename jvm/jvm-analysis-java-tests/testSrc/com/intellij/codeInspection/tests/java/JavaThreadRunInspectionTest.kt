package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.ThreadRunInspectionTestBase
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/threadrun"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class JavaThreadRunInspectionTest : ThreadRunInspectionTestBase() {
  override fun getBasePath() = JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  override val fileExt: String = "java"

  override fun setUp() {
    inspection = InspectionTestUtil.instantiateTool(inspection?.javaClass) // Load inspection config to load suppressId
    super.setUp()
  }

  fun `test highlighting`() = testHighlighting("ThreadRunTest")

  fun `test quickfix`() = testQuickFixAll("ThreadRunQfTest")
}