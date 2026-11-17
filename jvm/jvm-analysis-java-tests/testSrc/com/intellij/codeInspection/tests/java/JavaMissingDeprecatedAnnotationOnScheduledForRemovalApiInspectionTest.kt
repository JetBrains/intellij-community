package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.missingDeprecatedAnnotationOnScheduledForRemovalApiInspectionFixture
import com.intellij.jvm.analysis.internal.testFramework.missingDeprecatedAnnotationOnScheduledForRemovalApiProjectFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

private const val inspectionPath = "/codeInspection/missingDeprecatedAnnotationOnScheduledForRemovalApi"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
@TestApplication
class JavaMissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTest {
  companion object {
    private val projectFixture = missingDeprecatedAnnotationOnScheduledForRemovalApiProjectFixture()
  }

  private val myFixture by missingDeprecatedAnnotationOnScheduledForRemovalApiInspectionFixture(projectFixture, JavaJvmAnalysisTestUtil.testDataPath + inspectionPath)

  @Test
  fun `test missing @Deprecated on @ScheduledForRemoval APIs`() {
    myFixture.testHighlighting("missingDeprecatedAnnotations.java")
  }
}