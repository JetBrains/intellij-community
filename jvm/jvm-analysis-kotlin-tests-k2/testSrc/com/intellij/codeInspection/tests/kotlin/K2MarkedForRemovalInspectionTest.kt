package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.testFramework.JvmLanguage

class K2MarkedForRemovalInspectionTest : KotlinMarkedForRemovalInspectionTest() {

  fun `test highlighted as deprecated for removal`() {
    myFixture.addClass("""
      package test;
      @Deprecated(forRemoval = true)
      class MyTest { }
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      package test
      fun main() {
        <error descr="'test.MyTest' is deprecated and marked for removal"><warning descr="[DEPRECATION] 'constructor(): MyTest' is deprecated. Deprecated in Java.">MyTest</warning></error>()
      }
    """.trimIndent())
  }
}