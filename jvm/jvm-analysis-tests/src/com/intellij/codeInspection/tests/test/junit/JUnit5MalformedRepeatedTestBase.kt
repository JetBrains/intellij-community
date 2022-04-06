package com.intellij.codeInspection.tests.test.junit

import com.intellij.codeInspection.test.junit.JUnit5MalformedRepeatedTestInspection
import com.intellij.codeInspection.tests.UastInspectionTestBase

abstract class JUnit5MalformedRepeatedTestBase : UastInspectionTestBase() {
  override val inspection = JUnit5MalformedRepeatedTestInspection()

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      package org.junit.jupiter.api;
      @org.junit.platform.commons.annotation.Testable
      public @interface Test {}
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api;
      public @interface AfterEach {}
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api;
      public @interface BeforeAll {}
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api;
      public @interface BeforeEach {}
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api;
      public interface RepetitionInfo {}
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api;
      @org.junit.platform.commons.annotation.Testable
      public @interface RepeatedTest { 
        int value(); String name() default "";
      }
  """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.params;
      @org.junit.platform.commons.annotation.Testable
      public @interface ParameterizedTest { 
        String name() default "";
      }
  """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api;
      public interface TestInfo {}
  """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api;
      public interface TestReporter {}
  """.trimIndent())
  }
}