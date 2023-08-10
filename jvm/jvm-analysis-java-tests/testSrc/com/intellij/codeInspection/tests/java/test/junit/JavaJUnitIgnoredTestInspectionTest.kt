package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.codeInspection.tests.test.junit.JUnitIgnoredTestInspectionTestBase

class JavaJUnitIgnoredTestInspectionTest : JUnitIgnoredTestInspectionTestBase() {
  fun `test JUnit 4 @Ignore`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.*;

      @Ignore("for good reason")
      class IgnoredJUnitTest {
        @Ignore
        @Test
        public void <warning descr="Test method 'foo1()' is ignored/disabled without reason">foo1</warning>() { }
        
        @Ignore("valid description")
        @Test
        public void foo2() { }        
      }
    """.trimIndent())
  }

  fun `test JUnit 5 @Disabled`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.jupiter.api.Disabled;
      import org.junit.jupiter.api.Test;
      import org.junit.Ignore;

      @Disabled
      class <warning descr="Test class 'DisabledJUnit5Test' is ignored/disabled without reason">DisabledJUnit5Test</warning> {
        @Disabled
        @Test
        void <warning descr="Test method 'foo1()' is ignored/disabled without reason">foo1</warning>() { }
        
        @Disabled
        @Ignore("valid reason")
        @Test
        void foo2() { }        
      }
    """.trimIndent())
  }
}