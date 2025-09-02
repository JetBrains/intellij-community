package com.intellij.codeInspection.tests.java.test

import com.intellij.jvm.analysis.internal.testFramework.test.TestMethodWithoutAssertionInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

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

  fun `test no highlighting for AssertJ`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import junit.framework.TestCase;
      
      import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

      public class TestMethodWithAssertion extends TestCase {
        public void testExceptionOfType() {
            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(this::methodThatThrowsException);
        }
        
        private void methodThatThrowsException() {
            throw new UnsupportedOperationException("Not implemented yet");
        }
      }
    """.trimIndent(), "TestMethodWithAssertion")
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