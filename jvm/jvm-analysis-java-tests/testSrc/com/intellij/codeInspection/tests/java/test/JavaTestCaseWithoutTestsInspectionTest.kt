package com.intellij.codeInspection.tests.java.test

import com.intellij.jvm.analysis.internal.testFramework.test.testCaseWithoutTestsInspectionFixture
import com.intellij.jvm.analysis.internal.testFramework.test.testCaseWithoutTestsProjectFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class JavaTestCaseWithoutTestsInspectionTest {
  companion object {
    private val projectFixture = testCaseWithoutTestsProjectFixture()
  }

  private val myFixture by testCaseWithoutTestsInspectionFixture(projectFixture)

  @Test
  fun `test case without test methods in JUnit 3`() {
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

  @Test
  fun `test case with JUnit 3 inner class without test methods in JUnit 3`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class <warning descr="Test class 'TestCaseWithInner' has no tests">TestCaseWithInner</warning> extends junit.framework.TestCase {
        public static class Inner extends junit.framework.TestCase {
          public void test1() { }
        }
      }
    """.trimIndent(), fileName = "TestCaseWithInner")
  }

  @Test
  fun `test case without test methods but class is ignored in JUnit 3`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.Ignore
      public class IgnoredTest extends junit.framework.TestCase {}
    """.trimIndent(), "IgnoredTest")
  }

  @Test
  fun `test case with test in parent class in JUnit 3`() {
    myFixture.addClass("""
      public class SomeParentClassTest extends junit.framework.TestCase {
        public void testInParent() {}
      }
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class SomeTestClass extends SomeParentClassTest { }
    """.trimIndent(), "SomeTestClass")
  }

  @Test
  fun `test case without test methods in JUnit 4`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class <warning descr="Test class 'SomeTest' has no tests">SomeTest</warning> {
        @org.junit.Before
        public void foo() { }
      }
    """.trimIndent(), "SomeTest")
  }

  @Test
  fun `test case without test methods in JUnit 4 inheritance`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      abstract class SomeAbstractTest {
        private SomeAbstractTest() {} 
        @org.junit.Test
        public void foo() { }
        
        public static class SomeTest extends SomeAbstractTest {}
      }
      
    """.trimIndent(), "SomeTest")
  }

  @Test
  fun `test case with ignored test in JUnit 4`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class SomeTest {
        @org.junit.Before
        public void foo() { }
        
        @org.junit.Test
        @org.junit.Ignore
        public void myTest() { }
      }
    """.trimIndent(), "SomeTest")
  }

  @Test
  fun `test case with ignored test in TestNG`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class SomeTest {
        @org.testng.annotations.Test(enabled = false)
        public void foo() { }
      }
    """.trimIndent(), "SomeTest")
  }

  @Test
  fun `test case with nested class without test methods in JUnit 5`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class <warning descr="Test class 'TestCaseWithInner' has no tests">TestCaseWithInner</warning> {
        @org.junit.jupiter.api.Nested
        class <warning descr="Test class 'Inner' has no tests">Inner</warning> {
          void test1() {}
        }
      }
    """.trimIndent(), "TestCaseWithInner")
  }
}