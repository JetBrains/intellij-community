package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.overrideOnlyApiInspectionFixture
import com.intellij.jvm.analysis.internal.testFramework.overrideOnlyApiProjectFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

private val testDataPath: String = KotlinJvmAnalysisTestUtil.testDataPath + "/codeInspection/overrideOnly"

@TestDataPath("/testData/codeInspection/overrideOnly")
@TestApplication
abstract class KotlinOverrideOnlyApiInspectionTest {
  companion object {
    private val projectFixture = overrideOnlyApiProjectFixture(testDataPath)
  }

  protected val myFixture by overrideOnlyApiInspectionFixture(projectFixture, testDataPath)

  @Test
  fun `test invocations`() {
    myFixture.allowTreeAccessForAllFiles()
    myFixture.testHighlighting("plugin/KotlinCode.kt")
  }

  @Test
  fun `test delegation`() {
    myFixture.allowTreeAccessForAllFiles()
    myFixture.testHighlighting("plugin/KotlinCodeDelegate.kt")
  }

  @Test
  fun `test invalid annotation targets`() {
    myFixture.testHighlighting("plugin/KotlinInvalidAnnotationTargets.kt")
  }
}
