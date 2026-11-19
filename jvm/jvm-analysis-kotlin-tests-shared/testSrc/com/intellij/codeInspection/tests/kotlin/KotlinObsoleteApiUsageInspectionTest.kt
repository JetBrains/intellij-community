package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.jvmAnnotationsProjectFixture
import com.intellij.jvm.analysis.internal.testFramework.obsoleteApiUsageInspectionFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
abstract class KotlinObsoleteApiUsageInspectionTest {
  companion object {
    private val projectFixture = jvmAnnotationsProjectFixture()
  }

  protected val myFixture by obsoleteApiUsageInspectionFixture(projectFixture)

  

  @Test
  fun `test direct usage`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class B {
        fun f(a: A) {
          a.<warning descr="Obsolete API is used">f</warning>();
        }
      }
    """.trimIndent())
  }

  @Test
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

  @Test
  fun `test generic reference`() {
    myFixture.addClass("@org.jetbrains.annotations.ApiStatus.Obsolete public interface I<T> {}")
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
class U {
  fun u(i: <warning descr="Obsolete API is used">I</warning><Int>) = i
}
""".trimIndent())
  }
}