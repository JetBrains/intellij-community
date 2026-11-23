package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.missingDeprecatedAnnotationOnScheduledForRemovalApiInspectionFixture
import com.intellij.jvm.analysis.internal.testFramework.missingDeprecatedAnnotationOnScheduledForRemovalApiProjectFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

private const val inspectionPath = "/codeInspection/missingDeprecatedAnnotationOnScheduledForRemovalApi"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
@TestApplication
abstract class KotlinMissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTest {
  companion object {
    private val projectFixture = missingDeprecatedAnnotationOnScheduledForRemovalApiProjectFixture()
  }

  protected val myFixture by missingDeprecatedAnnotationOnScheduledForRemovalApiInspectionFixture(projectFixture, KotlinJvmAnalysisTestUtil.testDataPath + inspectionPath)

  @Test
  fun `test missing @Deprecated on @ScheduledForRemoval APIs`() {
    myFixture.testHighlighting("missingDeprecatedAnnotations.kt")
  }
}