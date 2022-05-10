package com.intellij.codeInspection.tests.test.junit

import com.intellij.codeInspection.test.junit.JUnitMalformedTestInspection
import com.intellij.codeInspection.tests.UastInspectionTestBase

abstract class JUnitMalformedTestInspectionTestBase : UastInspectionTestBase() {
  override val inspection = JUnitMalformedTestInspection()

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
     package org.junit;
     public @interface Test {
       Class<? extends Throwable> expected() default Test.None.class;
     }
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.runner;
      @Retention(RetentionPolicy.RUNTIME)
      @Target({ElementType.TYPE})
      @Inherited
      public @interface RunWith {
        Class<? extends Runner> value();
      }
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.runner;
      public abstract class Runner { }
    """.trimIndent())
    myFixture.addClass("""
      package junit.framework;
      public abstract class TestCase { }
    """.trimIndent())
    myFixture.addClass("""
      package mockit;
      @Retention(value=RUNTIME) @Target(value={FIELD,PARAMETER})
      public @interface Mocked { }
    """.trimIndent())
  }
}