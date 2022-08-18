package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitAssertEqualsOnArrayInspectionTestBase

class JavaJUnitAssertEqualsOnArrayInspectionTest : JUnitAssertEqualsOnArrayInspectionTestBase() {
  fun `test highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import org.junit.jupiter.api.Assertions;
      
      class MyTest {
        void myTest() {
          Object[] a = {};
          Object[] e = {""};
          Assertions.<warning descr="'assertEquals()' called on array">assertEquals</warning>(a, e, "message");
        }
      }      
    """.trimIndent())
  }

  fun `test quickfix`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      import org.junit.jupiter.api.Assertions;
      
      class MyTest {
        void myTest() {
          Object[] a = {};
          Object[] e = {""};
          Assertions.assert<caret>Equals(a, e, "message");
        }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.Assertions;
      
      class MyTest {
        void myTest() {
          Object[] a = {};
          Object[] e = {""};
          Assertions.assertArrayEquals(a, e, "message");
        }
      }
    """.trimIndent(), "Replace with 'assertArrayEquals()'")
  }
}