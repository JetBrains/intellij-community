package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitDatapointInspectionTestBase

class JavaJUnitDataPointInspectionTest : JUnitDatapointInspectionTestBase() {
  fun `test no highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint public static Object f1;
      }
    """.trimIndent())
  }

  fun `test non-static highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint public Object <warning descr="Fields annotated with @DataPoint should be 'static'">f1</warning>;
      }
    """.trimIndent())
  }

  fun `test non-public highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint static Object <warning descr="Fields annotated with @DataPoint should be 'public'">f1</warning>;
      }
    """.trimIndent())
  }

  fun `test field highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint Object <warning descr="Fields annotated with @DataPoint should be 'public' and 'static'">f1</warning>;
      }
    """.trimIndent())
  }

  fun `test method highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint Object <warning descr="Methods annotated with @DataPoint should be 'public' and 'static'">f1</warning>() { return null; }
      }
    """.trimIndent())
  }
}