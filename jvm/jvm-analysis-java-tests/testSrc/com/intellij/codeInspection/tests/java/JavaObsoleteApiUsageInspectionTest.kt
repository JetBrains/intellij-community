package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.codeInspection.tests.ObsoleteApiUsageInspectionTestBase

class JavaObsoleteApiUsageInspectionTest : ObsoleteApiUsageInspectionTestBase() {
  fun `test direct usage`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class B {
        void f(A a) {
          a.<warning descr="Obsolete API is used">f</warning>();
        }
      }
    """.trimIndent())
  }

  fun `test override`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class C extends A {
         void <warning descr="Obsolete API is used">f</warning>() {}
      }
      
      @org.jetbrains.annotations.ApiStatus.Obsolete 
      class D extends A {
         void <warning descr="Obsolete API is used">f</warning>() {}
      }      
    """.trimIndent())
  }
}