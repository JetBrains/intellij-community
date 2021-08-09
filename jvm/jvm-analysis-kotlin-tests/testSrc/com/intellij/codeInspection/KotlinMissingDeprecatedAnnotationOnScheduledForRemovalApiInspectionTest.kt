package com.intellij.codeInspection

import com.intellij.codeInspection.tests.MissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTestBase
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/missingDeprecatedAnnotationOnScheduledForRemovalApi"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinMissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTest
  : MissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTestBase()
{
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  override val fileExt: String = "kt"

  fun `test missing @Deprecated on @ScheduledForRemoval APIs`() = testHighlighting("missingDeprecatedAnnotations")
}