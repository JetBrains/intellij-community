package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitDatapointInspectionTestBase

class JavaJUnitDataPointInspectionTest : JUnitDatapointInspectionTestBase() {
  fun `test @DataPoint no highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint public static Object f1;
      }
    """.trimIndent())
  }

  fun `test @DataPoint non-static highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint public Object <warning descr="Fields annotated with 'org.junit.experimental.theories.DataPoint' should be static">f1</warning>;
      }
    """.trimIndent())
  }

  fun `test @DataPoint non-public highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint static Object <warning descr="Fields annotated with 'org.junit.experimental.theories.DataPoint' should be public">f1</warning>;
      }
    """.trimIndent())
  }

  fun `test @DataPoint field highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint Object <warning descr="Fields annotated with 'org.junit.experimental.theories.DataPoint' should be public and static">f1</warning>;
      }
    """.trimIndent())
  }

  fun `test @DataPoint method highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint Object <warning descr="Methods annotated with 'org.junit.experimental.theories.DataPoint' should be public and static">f1</warning>() { return null; }
      }
    """.trimIndent())
  }

  fun `test @DataPoints method highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoints Object <warning descr="Methods annotated with 'org.junit.experimental.theories.DataPoints' should be public and static">f1</warning>() { return null; }
      }
    """.trimIndent())
  }

  fun `test @DataPoint quickfix make method public and static`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint Object f<caret>1() { return null; }
      }
    """.trimIndent(), """
      class Test {
        @org.junit.experimental.theories.DataPoint
        public static Object f1() { return null; }
      }
    """.trimIndent(), "Make method 'f1' public and static")
  }
}