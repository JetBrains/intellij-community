package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.OverrideOnlyApiInspectionTestBase
import com.intellij.testFramework.TestDataPath

@TestDataPath("/testData/codeInspection/overrideOnly")
abstract class KotlinOverrideOnlyApiInspectionTest : OverrideOnlyApiInspectionTestBase() {

  

  fun `test invocations`() {
    myFixture.allowTreeAccessForAllFiles()
    myFixture.testHighlighting("plugin/KotlinCode.kt")
  }

  fun `test delegation`() {
    myFixture.allowTreeAccessForAllFiles()
    myFixture.testHighlighting("plugin/KotlinCodeDelegate.kt")
  }

  fun `test invalid annotation targets`() {
    myFixture.testHighlighting("plugin/KotlinInvalidAnnotationTargets.kt")
  }
}
