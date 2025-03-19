package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K1MarkedForRemovalInspectionTest : KotlinMarkedForRemovalInspectionTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K1

  fun `test highlighted as deprecated for removal`() {
    myFixture.addClass("""
      package test;
      @Deprecated(forRemoval = true)
      class MyTest { }
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      package test
      fun main() {
        <error descr="'test.MyTest' is deprecated and marked for removal"><warning descr="[DEPRECATION] 'MyTest' is deprecated. Deprecated in Java">MyTest</warning></error>()
      }
    """.trimIndent())
  }
}