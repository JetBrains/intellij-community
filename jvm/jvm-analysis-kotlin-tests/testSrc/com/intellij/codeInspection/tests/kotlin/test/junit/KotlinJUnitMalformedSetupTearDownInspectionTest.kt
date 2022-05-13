package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitMalformedSetupTearDownInspectionTestBase

class KotlinJUnitMalformedSetupTearDownInspectionTest : JUnitMalformedSetupTearDownInspectionTestBase() {
  fun `test setup() highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import junit.framework.TestCase
      class C : TestCase() {
        private fun <warning descr="'setUp()' has incorrect signature">setUp</warning>(i: Int) { System.out.println(i) }
      }  
    """.trimIndent())
  }

  fun `test setup() quickfix`() {
    myFixture.testQuickFix(ULanguage.KOTLIN, """
      import junit.framework.TestCase
      class C : TestCase() {
        private fun set<caret>Up(i: Int) { }
      }  
    """.trimIndent(), """
      import junit.framework.TestCase
      class C : TestCase() {
        fun setUp(): Unit { }
      }  
    """.trimIndent(), "Fix 'setUp' method signature")
  }
}