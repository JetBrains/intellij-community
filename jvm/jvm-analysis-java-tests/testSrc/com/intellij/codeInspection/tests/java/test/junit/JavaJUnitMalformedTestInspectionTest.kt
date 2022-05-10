package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitMalformedTestInspectionTestBase

class JavaJUnitMalformedTestInspectionTest : JUnitMalformedTestInspectionTestBase() {
  fun testJUnit3TestMethodIsPublicVoidNoArg() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      public class JUnit3TestMethodIsPublicVoidNoArg extends junit.framework.TestCase {
        public JUnit3TestMethodIsPublicVoidNoArg() { }
        void <warning descr="Test method 'testOne()' is not declared 'public void'">testOne</warning>() { }
        public int <warning descr="Test method 'testTwo()' is not declared 'public void'">testTwo</warning>() { return 2; }
        public static void <warning descr="Test method 'testThree()' should not be 'static'">testThree</warning>() { }
        public void <warning descr="Test method 'testFour()' should probably not have parameters">testFour</warning>(int i) { }
        public void testFive() { }
        void testSix(int i) { } //ignore when method doesn't look like test anymore
      }
    """.trimIndent())
  }

  fun testJUnit4TestMethodIsPublicVoidNoArg() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import org.junit.Test;
      public class JUnit4TestMethodIsPublicVoidNoArg {
        @Test JUnit4TestMethodIsPublicVoidNoArg() {}
        @Test void <warning descr="Test method 'testOne()' is not declared 'public void'">testOne</warning>() {}
        @Test public int <warning descr="Test method 'testTwo()' is not declared 'public void'">testTwo</warning>() { return 2; }
        @Test public static void <warning descr="Test method 'testThree()' should not be 'static'">testThree</warning>() {}
        @Test public void <warning descr="Test method 'testFour()' should probably not have parameters">testFour</warning>(int i) {}
        @Test public void testFive() {}
        @Test public void testMock(@mockit.Mocked String s) {}
      }
    """.trimIndent())
  }

  fun testJUnit4RunWith() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      @org.junit.runner.RunWith(org.junit.runner.Runner.class)
      class JUnit4RunWith {
          @org.junit.Test public int testMe(int i) { return -1; }
      }
    """.trimIndent())
  }
}