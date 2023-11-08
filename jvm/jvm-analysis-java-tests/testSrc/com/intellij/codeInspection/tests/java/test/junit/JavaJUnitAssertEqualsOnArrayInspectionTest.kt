package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.jvm.analysis.internal.testFramework.test.junit.JUnitAssertEqualsOnArrayInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaJUnitAssertEqualsOnArrayInspectionTest : JUnitAssertEqualsOnArrayInspectionTestBase() {
  fun `test highlighting`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
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
    myFixture.testQuickFix(JvmLanguage.JAVA, """
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