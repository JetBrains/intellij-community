package com.intellij.codeInspection

import com.intellij.codeInspection.tests.MissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil
import com.intellij.testFramework.TestDataPath

@TestDataPath("\$CONTENT_ROOT/testData/codeInspection/missingDeprecatedAnnotationOnScheduledForRemovalApi")
class KotlinMissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTest : MissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTestBase() {

  override fun getBasePath() = JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/missingDeprecatedAnnotationOnScheduledForRemovalApi"

  fun `test missing @Deprecated on @ScheduledForRemoval APIs`() {
    myFixture.testHighlighting("missingDeprecatedAnnotations.kt")
  }
}