package com.intellij.codeInspection.tests.kotlin.deadCode

import com.intellij.jvm.analysis.internal.testFramework.deadCode.AssertJImplicitUsageProviderTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinAssertJImplicitUsageProviderTest : AssertJImplicitUsageProviderTestBase(), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

  fun `test inject soft assertion implicit usage provider`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      @org.junit.jupiter.api.extension.ExtendWith(org.assertj.core.api.junit.jupiter.SoftAssertionsExtension::class)
      class TestClass {
          @org.assertj.core.api.junit.jupiter.InjectSoftAssertions
          private lateinit var softAssertions: org.assertj.core.api.SoftAssertions

          @org.junit.jupiter.api.Test
          fun doSomething() {
              softAssertions.assertThat("string").isEqualTo("string")
          }
      }
    """.trimIndent(), fileName = "TestClass")
  }
}