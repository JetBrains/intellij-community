package com.intellij.codeInspection.tests.java.test

import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.codeInspection.tests.test.TestMethodWithoutAssertionInspectionTestBase

class JavaTestMethodWithoutAssertionInspectionTest : TestMethodWithoutAssertionInspectionTestBase() {
  fun `test highlighting for empty method body`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import junit.framework.TestCase;
      import org.junit.Test;
      import org.junit.Assert;

      public class TestMethodWithoutAssertion extends TestCase {
          public void <warning descr="Test method 'test()' contains no assertions">test</warning>() { }

          @Test
          public void <warning descr="Test method 'fourOhTest()' contains no assertions">fourOhTest</warning>() { }

          @Test(expected = Exception.class)
          public void fourOhTestWithExpected() { }
      }
    """.trimIndent(), "TestMethodWithoutAssertion")
  }

  fun `test no highlighting when assertion is present`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import junit.framework.TestCase;
      import org.junit.Test;
      import org.junit.Assert;
      import mockit.*;

      public class TestMethodWithoutAssertion extends TestCase {
          @Test
          public void fourOhTest2() { Assert.assertTrue(true); }

          public void test2() { assertTrue(true); }

          public void test3() { fail(); }

          @Test 
          public void delegateOnly() { check(); }
          
          @Test 
          public void assertKeyword() { assert true; }

          @Test
          public void delegateAdditionally() {
              final int i = 9;
              check();
          }

          private void check() { Assert.assertTrue(true); }

          @Test
          public void testExecuteReverseAcknowledgement(@Mocked final Object messageDAO)  {
              System.out.println(messageDAO);

              new Verifications() { };
          }

          @Test
          public void testMethodWhichThrowsExceptionOnFailure() throws AssertionError {
              if (true) throw new AssertionError();
          }
      }
    """.trimIndent(), "TestMethodWithoutAssertion")
  }
}