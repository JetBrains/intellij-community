package com.intellij.codeInspection.tests.test.junit

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.test.junit.JUnitBeforeAfterClassInspection
import com.intellij.codeInspection.tests.UastInspectionTestBase

abstract class JUnitBeforeAfterClassInspectionTestBase : UastInspectionTestBase() {
  override val inspection: InspectionProfileEntry = JUnitBeforeAfterClassInspection()

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      package org.junit.jupiter.api;
      public @interface BeforeAll {}
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api;
      public @interface AfterAll {}
    """.trimIndent())
  }
}