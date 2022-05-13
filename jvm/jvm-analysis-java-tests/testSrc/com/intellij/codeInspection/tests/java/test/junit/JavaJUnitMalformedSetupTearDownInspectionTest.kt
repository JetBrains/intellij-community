package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitMalformedSetupTearDownInspectionTestBase

class JavaJUnitMalformedSetupTearDownInspectionTest : JUnitMalformedSetupTearDownInspectionTestBase() {
  fun `test setup() highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import junit.framework.TestCase;
      class C extends TestCase {
        private void <warning descr="'setUp()' has incorrect signature">setUp</warning>(int i) { }
      }  
    """.trimIndent())
  }

  fun `test setup() quickfix`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      import junit.framework.TestCase;
      class C extends TestCase {
        private void set<caret>Up(int i) { }
      }  
    """.trimIndent(), """
      import junit.framework.TestCase;
      class C extends TestCase {
        public void setUp() { }
      }  
    """.trimIndent(), "Fix 'setUp' method signature")
  }
}