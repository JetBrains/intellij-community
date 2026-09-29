package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.MissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTestBase
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/missingDeprecatedAnnotationOnScheduledForRemovalApi"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class JavaMissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTest : MissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTestBase() {
  override fun getTestDataPath(): String = JavaJvmAnalysisTestUtil.testDataPath + inspectionPath


  fun `test missing @Deprecated on @ScheduledForRemoval APIs`() {
    myFixture.testHighlighting("missingDeprecatedAnnotations.java")
  }
}