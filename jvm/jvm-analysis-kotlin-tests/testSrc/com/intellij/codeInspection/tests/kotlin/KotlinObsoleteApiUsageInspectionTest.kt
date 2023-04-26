package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.codeInspection.tests.ObsoleteApiUsageInspectionTestBase

class KotlinObsoleteApiUsageInspectionTest : ObsoleteApiUsageInspectionTestBase() {
  fun `test direct usage`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class B {
        fun f(a: A) {
          a.<warning descr="Obsolete API is used">f</warning>();
        }
      }
    """.trimIndent())
  }

  fun `test override`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class C : A() {
         override fun <warning descr="Obsolete API is used">f</warning>() { }
      }
      
      @org.jetbrains.annotations.ApiStatus.Obsolete 
      class D : A() {
         override fun <warning descr="Obsolete API is used">f</warning>() { }
      }      
    """.trimIndent())
  }
}