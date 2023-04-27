package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.codeInspection.tests.test.junit.JUnit3SuperTearDownInspectionTestBase

class JavaJUnit3SuperTearDownInspectionTest : JUnit3SuperTearDownInspectionTestBase() {
  fun `test teardown in finally no highlighting`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class NoProblem extends junit.framework.TestCase {
        public void tearDown() throws Exception {
          super.tearDown();
        }
      }
      class CalledInFinally extends junit.framework.TestCase {
        public void tearDown() throws Exception {
          try {
            System.out.println();
          } finally {
            super.tearDown();
          }
        }
      }
      class SomeTest extends junit.framework.TestCase {
        @Override
        protected void setUp() throws Exception {
          try {
            super.setUp();
          }
          catch (Throwable t) {
            super.tearDown();
          }
        }
        public void test_something() {}
      }
    """.trimIndent())
  }

  fun `test teardown in finally highlighting`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class SuperTearDownInFinally extends junit.framework.TestCase {
        @Override
        public void tearDown() throws Exception {
          super.<warning descr="'tearDown()' is not called from 'finally' block">tearDown</warning>();
          System.out.println();
        }
      }      
    """.trimIndent())
  }
}