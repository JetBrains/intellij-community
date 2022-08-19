package com.intellij.codeInspection.tests.java.test

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.TestCaseWithConstructorInspectionTestBase

class JavaTestCaseWithConstructorInspectionTest : TestCaseWithConstructorInspectionTestBase() {
  fun `test no highlighting parameterized test case`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import org.junit.Test;
      import org.junit.runner.RunWith;
      import org.junit.runners.Parameterized;
      import org.junit.runners.Parameterized.Parameters;

      @RunWith(Parameterized.class)
      public class ParameterizedTest {

        private final int myX;
        private final int myY;

        public ParameterizedTest(int x, int y) {
          myX = x;
          myY = y;
        }

        @Test
        public void testMe() {
          System.out.println(myX * myY);
        }

        @Parameterized.Parameters
        public static Object[][] parameters() {
          return new Object[][] {{1, 2}, {3, 4}};
        }
      }
    """.trimIndent(), "ParameterizedTest")
  }

  fun `test no highlighting trivial constructor`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import junit.framework.TestCase;
      
      public class TestCaseWithConstructorInspection2 extends TestCase {
          public TestCaseWithConstructorInspection2() {
              super();
              ;
              if (false) {
                  System.out.println();
              }
          }
      }
    """.trimIndent(), "TestCaseWithConstructorInspection2")
  }

  fun `test highlighting simple non-trivial constructor`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import junit.framework.TestCase;

      public class TestCaseWithConstructorInspection1 extends TestCase {
          public <warning descr="Initialization logic in constructor 'TestCaseWithConstructorInspection1()' instead of 'setup()' life cycle method">TestCaseWithConstructorInspection1</warning>() {
              System.out.println("");
          }
      }
    """.trimIndent(), "TestCaseWithConstructorInspection1")
  }

  fun `test highlighting complex non-trivial constructor`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import junit.framework.TestCase;
      
      public class TestCaseWithConstructorInspection3 extends TestCase {
          public <warning descr="Initialization logic in constructor 'TestCaseWithConstructorInspection3()' instead of 'setup()' life cycle method">TestCaseWithConstructorInspection3</warning>() {
              super();
              System.out.println("TestCaseWithConstructorInspection3.TestCaseWithConstructorInspection3");
          }
      
      }
    """.trimIndent(), "TestCaseWithConstructorInspection3")
  }

  fun `test highlighting Junit 4`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      public class JUnit4TestCaseWithConstructor {
        public <warning descr="Initialization logic in constructor 'JUnit4TestCaseWithConstructor()' instead of 'setup()' life cycle method">JUnit4TestCaseWithConstructor</warning>() {
          System.out.println();
          System.out.println();
          System.out.println();
        }

        @org.junit.Test
        public void testMe() {}
      }
    """.trimIndent(), "JUnit4TestCaseWithConstructor")
  }
}