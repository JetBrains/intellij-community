package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class K2MarkedForRemovalInspectionTest : KotlinMarkedForRemovalInspectionTest() {

  @Test
  fun `test highlighted as deprecated for removal`() {
    myFixture.addClass("""
      package test;
      @Deprecated(forRemoval = true)
      class MyTest { }
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      package test
      fun main() {
        <error descr="'test.MyTest' is deprecated and marked for removal"><warning descr="[DEPRECATION]">MyTest</warning></error>()
      }
    """.trimIndent())
  }

  @Test
  fun `test call of override of non-deprecated interface method is not reported`() {
    myFixture.addClass("""
      package test;
      public interface A {
        void foo();
      }
    """.trimIndent())
    myFixture.addClass("""
      package test;
      @Deprecated(forRemoval = true)
      public class B implements A {
        @Override public void foo() { }
        public void bar() { }
      }
    """.trimIndent())
    myFixture.addClass("""
      package test;
      public class C extends B { }
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      package test
      fun test(c: C) {
        c.foo()
        c.<error descr="'test.B' is deprecated and marked for removal">bar</error>()
      }
    """.trimIndent())
  }
}