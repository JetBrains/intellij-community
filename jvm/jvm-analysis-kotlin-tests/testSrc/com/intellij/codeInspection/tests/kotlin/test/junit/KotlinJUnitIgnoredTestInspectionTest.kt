package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.jvm.analysis.internal.testFramework.test.junit.JUnitIgnoredTestInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class KotlinJUnitIgnoredTestInspectionTest : JUnitIgnoredTestInspectionTestBase() {
  fun `test JUnit 4 @Ignore`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.junit.*

      @Ignore("for good reason")
      class IgnoredJUnitTest {
        @Ignore
        @Test
        public fun <warning descr="Test method 'foo1()' is ignored/disabled without reason">foo1</warning>() { }
        
        @Ignore("valid description")
        @Test
        public fun foo2() { }        
      }
    """.trimIndent())
  }

  fun `test JUnit 5 @Disabled`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.junit.jupiter.api.Disabled
      import org.junit.jupiter.api.Test
      import org.junit.Ignore

      @Disabled
      class <warning descr="Test class 'DisabledJUnit5Test' is ignored/disabled without reason">DisabledJUnit5Test</warning> {
        @Disabled
        @Test
        fun <warning descr="Test method 'foo1()' is ignored/disabled without reason">foo1</warning>() { }
        
        @Disabled
        @Ignore("valid reason")
        @Test
        fun foo2() { }        
      }
    """.trimIndent())
  }
}