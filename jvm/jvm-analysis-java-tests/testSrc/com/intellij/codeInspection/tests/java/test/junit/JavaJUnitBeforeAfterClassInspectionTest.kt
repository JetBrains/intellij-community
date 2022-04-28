package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitBeforeAfterClassInspectionTestBase

class JavaJUnitBeforeAfterClassInspectionTest : JUnitBeforeAfterClassInspectionTestBase() {
  fun testHighlighting() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import org.junit.jupiter.api.BeforeAll;
      
      class MainTest {
        @BeforeAll
        String <warning descr="'beforeAll()' has incorrect signature for a '@org.junit.jupiter.api.BeforeAll' method">beforeAll</warning>(int i) { return ""; }
      }
    """.trimIndent())
  }

  fun testQuickFix() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      import org.junit.jupiter.api.BeforeAll;
      
      class MainTest {
        @BeforeAll
        String before<caret>All(int i) { return ""; }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.BeforeAll;
      
      class MainTest {
        @BeforeAll
        public static void beforeAll() { return ""; }
      }
    """.trimIndent(), "Fix 'beforeAll' method signature")
  }
}