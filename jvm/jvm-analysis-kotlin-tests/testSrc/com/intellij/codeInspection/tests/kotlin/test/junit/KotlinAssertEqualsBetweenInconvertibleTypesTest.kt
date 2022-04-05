package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.tests.test.junit.AssertEqualsBetweenInconvertibleTypesInspectionTestBase
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.TestDataPath
import java.io.File

private const val inspectionPath = "/codeInspection/assertEqualsBetweenInconvertibleTypes"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinAssertEqualsBetweenInconvertibleTypesTest : AssertEqualsBetweenInconvertibleTypesInspectionTestBase() {
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  override fun getTestDataPath(): String = PathManager.getCommunityHomePath().replace(File.separatorChar, '/') + basePath

  fun `test AssertEqualsBetweenInconvertibleTypes`() {
    myFixture.testHighlighting("AssertEqualsBetweenInconvertibleTypes.kt")
  }
}
