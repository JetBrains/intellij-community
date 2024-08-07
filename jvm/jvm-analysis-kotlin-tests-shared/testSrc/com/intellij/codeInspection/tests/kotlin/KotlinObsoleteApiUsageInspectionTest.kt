package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.ObsoleteApiUsageInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinObsoleteApiUsageInspectionTest : ObsoleteApiUsageInspectionTestBase(), KotlinPluginModeProvider {
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

  fun `test generic reference`() {
    myFixture.addClass("@org.jetbrains.annotations.ApiStatus.Obsolete public interface I<T> {}")
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
class U {
  fun u(i: <warning descr="Obsolete API is used">I</warning><Int>) = i
}
""".trimIndent())
  }
}