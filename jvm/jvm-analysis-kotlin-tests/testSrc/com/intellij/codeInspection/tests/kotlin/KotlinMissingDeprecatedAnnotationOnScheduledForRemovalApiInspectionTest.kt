package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.MissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTestBase
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/missingDeprecatedAnnotationOnScheduledForRemovalApi"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinMissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTest21
  : MissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTestBase()
{
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  fun `test missing @Deprecated on @ScheduledForRemoval APIs`() {
    myFixture.testHighlighting("missingDeprecatedAnnotations.kt")
  }
}