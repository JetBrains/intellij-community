package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitBeforeAfterClassInspectionTestBase

class JavaJUnitBeforeAfterClassInspectionTest : JUnitBeforeAfterClassInspectionTestBase() {
  fun testHighlighting() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        String <warning descr="'beforeAll()' has incorrect signature for a '@org.junit.jupiter.api.BeforeAll' method">beforeAll</warning>(int i) { return ""; }
      }
    """.trimIndent())
  }

  fun testNoHighlighting() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        public static void beforeAll() { }
      }
    """.trimIndent())
  }

  fun testQuickFix() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        String before<caret>All(int i) { return ""; }
      }
    """.trimIndent(), """
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        public static void beforeAll() { return ""; }
      }
    """.trimIndent(), "Fix 'beforeAll' method signature")
  }
}