package com.intellij.codeInspection.tests.test.junit

import com.intellij.codeInspection.test.junit.JUnitDataPointInspection
import com.intellij.codeInspection.tests.UastInspectionTestBase

abstract class JUnitDatapointInspectionTestBase : UastInspectionTestBase() {
  override val inspection = JUnitDataPointInspection()

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      package org.junit.experimental.theories;
      import java.lang.annotation.Target;
      @Target({FIELD, METHOD})
      public @interface DataPoint { }
    """.trimIndent())
  }
}