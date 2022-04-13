package com.intellij.codeInspection.tests

import com.intellij.codeInspection.test.junit.JUnitUnconstructableTestCaseInspection

abstract class JUnitUnconstructableTestCaseTestBase : UastInspectionTestBase() {
  override val inspection = JUnitUnconstructableTestCaseInspection()

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      package junit.framework;
      
      public abstract class TestCase { }
    """.trimIndent())
    myFixture.addClass("""
      package org.junit; 
      
      public @interface Test {
        Class<? extends Throwable> expected() default Test.None.class;
      }
    """.trimIndent())
  }
}