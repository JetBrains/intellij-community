package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.MissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTestBase
import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

private const val inspectionPath = "/codeInspection/missingDeprecatedAnnotationOnScheduledForRemovalApi"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
abstract class KotlinMissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTest : MissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTestBase(), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  fun `test missing @Deprecated on @ScheduledForRemoval APIs`() {
    myFixture.testHighlighting("missingDeprecatedAnnotations.kt")
  }
}