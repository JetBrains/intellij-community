package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.NonExtendableApiInspectionTestBase
import com.intellij.testFramework.TestDataPath

@TestDataPath("/testData/codeInspection/nonExtendableApiUsage")
abstract class KotlinNonExtendableApiInspectionTest : NonExtendableApiInspectionTestBase() {

  

  fun `test extensions`() {
    myFixture.allowTreeAccessForAllFiles()
    myFixture.testHighlighting("plugin/kotlinExtensions.kt")
  }

  fun `test invalid annotation targets`() {
    myFixture.allowTreeAccessForAllFiles()
    myFixture.testHighlighting("plugin/KotlinInvalidAnnotationTargets.kt")
  }
}
