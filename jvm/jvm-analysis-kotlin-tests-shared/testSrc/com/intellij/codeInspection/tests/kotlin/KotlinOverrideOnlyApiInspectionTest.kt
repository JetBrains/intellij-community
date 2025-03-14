package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.OverrideOnlyApiInspectionTestBase
import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

@TestDataPath("/testData/codeInspection/overrideOnly")
abstract class KotlinOverrideOnlyApiInspectionTest : OverrideOnlyApiInspectionTestBase(), ExpectedPluginModeProvider {

  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

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
