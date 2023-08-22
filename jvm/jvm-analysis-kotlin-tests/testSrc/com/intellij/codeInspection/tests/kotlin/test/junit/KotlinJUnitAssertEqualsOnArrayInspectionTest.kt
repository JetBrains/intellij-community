package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.codeInspection.tests.test.junit.JUnitAssertEqualsOnArrayInspectionTestBase

class KotlinJUnitAssertEqualsOnArrayInspectionTest : JUnitAssertEqualsOnArrayInspectionTestBase() {
  fun `test highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.junit.jupiter.api.Assertions
      
      class MyTest {
          fun myTest() {
              val a = arrayOf<Any>()
              val e = arrayOf<String>("")
              Assertions.<warning descr="'assertEquals()' called on array">assertEquals</warning>(a, e, "message")
          }
      }      
    """.trimIndent())
  }

  fun `test quickfix`() {
    myFixture.testQuickFix(JvmLanguage.KOTLIN, """
      import org.junit.jupiter.api.Assertions
      
      class MyTest {
          fun myTest() {
              val a = arrayOf<Any>()
              val e = arrayOf<String>("")
              Assertions.assert<caret>Equals(a, e, "message")
          }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.Assertions
      
      class MyTest {
          fun myTest() {
              val a = arrayOf<Any>()
              val e = arrayOf<String>("")
              Assertions.assertArrayEquals(a, e, "message")
          }
      }
    """.trimIndent(), "Replace with 'assertArrayEquals()'")
  }
}