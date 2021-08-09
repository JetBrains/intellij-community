package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.MissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisTestsUtil
import com.intellij.testFramework.TestDataPath

@TestDataPath("\$CONTENT_ROOT/testData/codeInspection/missingDeprecatedAnnotationOnScheduledForRemovalApi")
class JavaMissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTest : MissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTestBase() {
  override val fileExt: String = "java"

  override fun getTestDataPath() = JvmAnalysisTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/missingDeprecatedAnnotationOnScheduledForRemovalApi"

  fun `test missing @Deprecated on @ScheduledForRemoval APIs`() = testHighlighting("missingDeprecatedAnnotations")
}