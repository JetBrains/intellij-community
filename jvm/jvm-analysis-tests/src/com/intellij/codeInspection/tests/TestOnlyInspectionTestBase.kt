package com.intellij.codeInspection.tests

import com.intellij.codeInspection.TestOnlyInspection

abstract class TestOnlyInspectionTestBase : UastInspectionTestBase() {
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