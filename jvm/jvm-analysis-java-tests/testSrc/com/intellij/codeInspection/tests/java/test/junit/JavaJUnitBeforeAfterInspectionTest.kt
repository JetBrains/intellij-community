package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitBeforeAfterInspectionTestBase

class JavaJUnitBeforeAfterInspectionTest : JUnitBeforeAfterInspectionTestBase() {
  fun testHighlightingBefore() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import org.junit.Before;
      
      class MainTest {
        @Before
        String <warning descr="'before()' has incorrect signature for a '@org.junit.Before' method">before</warning>(int i) { return ""; }
      }
    """.trimIndent())
  }

  fun testHighlightingBeforeEach() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import org.junit.jupiter.api.BeforeEach;
      
      class MainTest {
        @BeforeEach
        String <warning descr="'beforeEach()' has incorrect signature for a '@org.junit.jupiter.api.BeforeEach' method">beforeEach</warning>(int i) { return ""; }
      }
    """.trimIndent())
  }

  fun testQuickFixChangeSignature() {
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
    """.trimIndent(), "Fix 'before' method signature")
  }

  fun testQuickFixChangeRemoveModifier() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      import org.junit.jupiter.api.BeforeEach;
      
      class MainTest {
        @BeforeEach
        private void bef<caret>oreEach() { }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.BeforeEach;
      
      class MainTest {
        @BeforeEach
        void bef<caret>oreEach() { }
      }
    """.trimIndent(), "Make 'beforeEach' not private")
  }
}