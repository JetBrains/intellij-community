package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.NonExtendableApiInspectionTestBase
import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

@TestDataPath("/testData/codeInspection/nonExtendableApiUsage")
abstract class KotlinNonExtendableApiInspectionTest : NonExtendableApiInspectionTestBase(), ExpectedPluginModeProvider {

  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

  fun `test extensions`() {
    myFixture.allowTreeAccessForAllFiles()
    myFixture.testHighlighting("plugin/kotlinExtensions.kt")
  }

  fun `test invalid annotation targets`() {
    myFixture.allowTreeAccessForAllFiles()
    myFixture.testHighlighting("plugin/KotlinInvalidAnnotationTargets.kt")
  }
}
