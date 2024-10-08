package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.deprecation.MarkedForRemovalInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinMarkedForRemovalInspectionTest : JvmInspectionTestBase(), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

  fun `test highlighted as deprecated for removal`() {
    myFixture.addClass("""
      package test;
      @Deprecated(forRemoval = true)
      class MyTest {}
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      package test
      fun main() {
        <error descr="'test.MyTest' is deprecated and marked for removal"><warning descr="[DEPRECATION] 'MyTest' is deprecated. Deprecated in Java">MyTest</warning></error>()
      }
    """.trimIndent())
  }

  override val inspection = MarkedForRemovalInspection()
}