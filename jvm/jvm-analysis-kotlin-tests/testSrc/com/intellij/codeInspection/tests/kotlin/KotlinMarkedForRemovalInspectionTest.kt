package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.deprecation.MarkedForRemovalInspection
import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.UastInspectionTestBase

class KotlinMarkedForRemovalInspectionTest : UastInspectionTestBase() {
  fun `test highlighted as deprecated for removal`() {
    myFixture.addClass("""
      package test;
      @Deprecated(forRemoval = true)
      class MyTest {}
    """.trimIndent())
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      package test
      fun main() {
        <error descr="'test.MyTest' is deprecated and marked for removal"><warning descr="[DEPRECATION] 'MyTest' is deprecated. Deprecated in Java">MyTest</warning></error>()
      }
    """.trimIndent())
  }

  override val inspection = MarkedForRemovalInspection()
}