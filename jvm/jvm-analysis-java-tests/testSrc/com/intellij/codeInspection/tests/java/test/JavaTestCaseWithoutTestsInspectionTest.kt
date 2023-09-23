package com.intellij.codeInspection.tests.java.test

import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.codeInspection.tests.test.TestCaseWithoutTestsInspectionTestBase

class JavaTestCaseWithoutTestsInspectionTest : TestCaseWithoutTestsInspectionTestBase() {
  fun `test case without test methods`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class <warning descr="Test class 'TestCaseWithNoTestMethods' has no tests">TestCaseWithNoTestMethods</warning> extends junit.framework.TestCase {
        public void setUp() throws Exception {
          super.setUp();
        }
        
        public void tearDown() throws Exception {
          super.tearDown();
        }
      
        public int testOne() {
          return 1;
        }
      
        public static void testTwo() { }
        
        void testThree() {}
        
        public void testFour(int i) { }
      }
    """.trimIndent(), fileName = "TestCaseWithNoTestMethods")
  }

  fun `test case with JUnit 3 inner class without test methods`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class <warning descr="Test class 'TestCaseWithInner' has no tests">TestCaseWithInner</warning> extends junit.framework.TestCase {
        public static class Inner extends junit.framework.TestCase {
          public void test1() { }
        }
      }
    """.trimIndent(), fileName = "TestCaseWithInner")
  }

  fun `test case with JUnit 5 nested class without test methods`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class <warning descr="Test class 'TestCaseWithInner' has no tests">TestCaseWithInner</warning> {
        @org.junit.jupiter.api.Nested
        class <warning descr="Test class 'Inner' has no tests">Inner</warning> {
          void test1() {}
        }
      }
    """.trimIndent(), "TestCaseWithInner")
  }

  fun `test case without test methods but class is ignored`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.Ignore
      public class IgnoredTest extends junit.framework.TestCase {}
    """.trimIndent(), "IgnoredTest")
  }

  fun `test case with test in parent class`() {
    myFixture.addClass("""
      public class SomeParentClass extends junit.framework.TestCase {
        protected SomeParentClass(String name) {}
        public void testInParent() {}
      }
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class SomeTestClass extends SomeParentClass {
        public SomeTestClass() {
          super("");
        }
      }
    """.trimIndent(), "SomeTestClass")
  }
}