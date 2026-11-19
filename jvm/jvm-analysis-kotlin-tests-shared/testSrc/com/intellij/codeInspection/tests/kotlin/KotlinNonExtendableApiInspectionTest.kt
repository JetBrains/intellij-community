package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.nonExtendableApiInspectionFixture
import com.intellij.jvm.analysis.internal.testFramework.nonExtendableApiProjectFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

private val testDataPath: String = KotlinJvmAnalysisTestUtil.testDataPath + "/codeInspection/nonExtendableApiUsage"

@TestDataPath("/testData/codeInspection/nonExtendableApiUsage")
@TestApplication
abstract class KotlinNonExtendableApiInspectionTest {
  companion object {
    private val projectFixture = nonExtendableApiProjectFixture(testDataPath)
  }

  protected val myFixture by nonExtendableApiInspectionFixture(projectFixture, testDataPath)

  @Test
  fun `test extensions`() {
    myFixture.allowTreeAccessForAllFiles()
    myFixture.testHighlighting("plugin/kotlinExtensions.kt")
  }

  @Test
  fun `test invalid annotation targets`() {
    myFixture.allowTreeAccessForAllFiles()
    myFixture.testHighlighting("plugin/KotlinInvalidAnnotationTargets.kt")
  }
}
