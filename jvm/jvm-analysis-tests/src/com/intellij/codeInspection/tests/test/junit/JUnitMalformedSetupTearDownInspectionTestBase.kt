package com.intellij.codeInspection.tests.test.junit

import com.intellij.codeInspection.test.junit.JUnitMalformedSetupTearDownInspection
import com.intellij.codeInspection.tests.UastInspectionTestBase

abstract class JUnitMalformedSetupTearDownInspectionTestBase : UastInspectionTestBase() {
  override val inspection = JUnitMalformedSetupTearDownInspection()

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      package junit.framework;
      public abstract class TestCase {
        protected void setUp() { }
        protected void tearDown() { }
      }
    """.trimIndent())
  }
}