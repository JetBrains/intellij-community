package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitBeforeAfterClassInspectionTestBase

class JavaJUnitBeforeAfterClassInspectionTest : JUnitBeforeAfterClassInspectionTestBase() {
  fun `test highlighting @BeforeAll complete wrong signature `() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        String <warning descr="'beforeAll()' has incorrect signature for a '@org.junit.jupiter.api.BeforeAll' method">beforeAll</warning>(int i) { return ""; }
      }
    """.trimIndent())
  }

  fun `test @BeforeAll no highlighting test instance per class`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import org.junit.jupiter.api.TestInstance;
      
      @TestInstance(TestInstance.Lifecycle.PER_CLASS)
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        public static void beforeAll() { }
      }
    """.trimIndent())
  }

  fun `test @BeforeAll no highlighting static`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        public static void beforeAll() { }
      }
    """.trimIndent())
  }

  fun `test parameter resolver no highlighting`() {
    myFixture.addClass("""
      package com.intellij.test;
      import org.junit.jupiter.api.extension.ParameterResolver;
      public class TestParameterResolver implements ParameterResolver { }
    """.trimIndent())

    myFixture.testHighlighting(ULanguage.JAVA, """
      import org.junit.jupiter.api.extension.*;
      import com.intellij.test.TestParameterResolver;
      
      @ExtendWith(TestParameterResolver.class)
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        public static void beforeAll(String foo) { }
      }
    """.trimIndent())
  }

  fun `test change signature quickfix`() {
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