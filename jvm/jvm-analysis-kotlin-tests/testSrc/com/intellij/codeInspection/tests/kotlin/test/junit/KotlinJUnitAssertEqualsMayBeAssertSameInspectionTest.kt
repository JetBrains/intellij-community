package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.jvm.analysis.internal.testFramework.test.junit.JUnitAssertEqualsMayBeAssertSameInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class KotlinJUnitAssertEqualsMayBeAssertSameInspectionTest : JUnitAssertEqualsMayBeAssertSameInspectionTestBase() {
  fun `test JUnit 3 highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class Test : junit.framework.TestCase() { 
          fun testOne() { 
              <warning descr="'assertEquals()' may be 'assertSame()'">assertEquals</warning>(A.a, A.b)
          } 
      }
    """.trimIndent())
  }

  fun `test JUnit 3 quickfix`() {
    myFixture.testQuickFix(JvmLanguage.KOTLIN, """
      class Test : junit.framework.TestCase() { 
          fun testOne() {
              asser<caret>tEquals(A.a, A.b)
          } 
      }
    """.trimIndent(), """
      class Test : junit.framework.TestCase() { 
          fun testOne() {
              assertSame(A.a, A.b)
          } 
      }
    """.trimIndent(), "Replace with 'assertSame()'")
  }

  fun `test JUnit 4 highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class Test { 
          @org.junit.Test 
          fun test() { 
              org.junit.Assert.<warning descr="'assertEquals()' may be 'assertSame()'">assertEquals</warning>(A.a, A.b)
          } 
      }
    """.trimIndent())
  }

  fun `test JUnit 4 quickfix`() {
    myFixture.testQuickFix(JvmLanguage.KOTLIN, """
      class Test { 
          @org.junit.Test 
          fun test() {
              org.junit.Assert.assert<caret>Equals(A.a, A.b)
          } 
      }
    """.trimIndent(), """
      import org.junit.Assert
      
      class Test { 
          @org.junit.Test 
          fun test() {
              Assert.assertSame(A.a, A.b)
          } 
      }
    """.trimIndent(), "Replace with 'assertSame()'")
  }
}