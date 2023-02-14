package com.intellij.codeInspection.tests.test

import com.intellij.codeInspection.test.TestOnlyInspection
import com.intellij.codeInspection.tests.JvmInspectionTestBase

abstract class TestOnlyInspectionTestBase : JvmInspectionTestBase() {
  override val inspection = TestOnlyInspection()

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      package org.jetbrains.annotations;
      
      import java.lang.annotation.*;
      
      @Documented
      @Retention(RetentionPolicy.CLASS)
      @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.TYPE})
      public @interface VisibleForTesting { }
  """.trimIndent())

    myFixture.addClass("""
      package org.jetbrains.annotations;
      
      import java.lang.annotation.*;
      
      @Documented
      @Retention(RetentionPolicy.CLASS)
      @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.TYPE})
      public @interface TestOnly { }
  """.trimIndent())
  }
}