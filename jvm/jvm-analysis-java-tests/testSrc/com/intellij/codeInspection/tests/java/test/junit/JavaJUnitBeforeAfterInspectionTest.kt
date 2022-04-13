package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitBeforeAfterInspectionTestBase

class JavaJUnitBeforeAfterInspectionTest : JUnitBeforeAfterInspectionTestBase() {
  fun testHighlighting() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import org.junit.Before;
      
      class MainTest {
        @Before
        String <warning descr="'before()' has incorrect signature for a '@org.junit.Before' method">before</warning>(int i) { return ""; }
      }
    """.trimIndent())
  }

  fun testQuickFix() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      import org.junit.Before;
      
      class MainTest {
        @Before
        String bef<caret>ore(int i) { return ""; }
      }
    """.trimIndent(), """
      import org.junit.Before;
      
      class MainTest {
        @Before
        public void before() { return ""; }
      }
    """.trimIndent(), "Change signature of 'String before(int)' to 'public void before()'")
  }
}