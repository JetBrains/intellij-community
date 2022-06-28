package com.intellij.codeInspection.tests.test.junit

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.test.junit.JUnitBeforeAfterClassInspection
import com.intellij.codeInspection.tests.UastInspectionTestBase

abstract class JUnitBeforeAfterClassInspectionTestBase : UastInspectionTestBase() {
  override val inspection: InspectionProfileEntry = JUnitBeforeAfterClassInspection()

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      package org.junit;
      public @interface BeforeClass { }
    """.trimIndent())
    myFixture.addClass("""
      package org.junit;
      public @interface AfterClass { }
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api;
      public @interface BeforeAll { }
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api;
      public @interface AfterAll { }
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api;
      public @interface TestInstance {
        Lifecycle value();
        enum Lifecycle { PER_CLASS, PER_METHOD }
      }
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api.extension;
      public @interface ExtendWith {
        Class<? extends Extension>[] value();
      }
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api.extension;
      public interface Extension { }
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api.extension;
      public interface ParameterResolver extends Extension { }
    """.trimIndent())
  }
}