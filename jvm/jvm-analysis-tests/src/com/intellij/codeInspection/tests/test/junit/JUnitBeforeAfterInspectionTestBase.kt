package com.intellij.codeInspection.tests.test.junit

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.test.junit.JUnitBeforeAfterInspection
import com.intellij.codeInspection.tests.UastInspectionTestBase

abstract class JUnitBeforeAfterInspectionTestBase : UastInspectionTestBase() {
  override val inspection: InspectionProfileEntry = JUnitBeforeAfterInspection()

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      package org.junit;
      public @interface Before {}
    """.trimIndent())
    myFixture.addClass("""
      package org.junit;
      public @interface After {}
    """.trimIndent())
  }
}