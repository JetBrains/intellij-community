package com.intellij.codeInspection.tests.test.junit

import com.intellij.codeInspection.test.junit.JUnitUnconstructableTestCaseInspection
import com.intellij.codeInspection.tests.UastInspectionTestBase

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
    myFixture.addClass("""
      package org.junit.runner;
      
      public abstract class Runner implements Describable { }
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.runner;
      
      public class Parameterized extends Runner {
        public @interface Parameters {
            String name() default "{index}";
        }
      }
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.runner;
      
      public @interface RunWith {
          Class<? extends Runner> value();
      }
    """.trimIndent())
  }
}