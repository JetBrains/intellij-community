package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.SameParameterValueInspectionTestBase

class SameParameterValueLocalInspectionTest : SameParameterValueInspectionTestBase(true) {
  fun testEntryPoint() {
    doHighlightTest(runDeadCodeFirst = true)
  }

  fun testMethodWithSuper() {
    doHighlightTest()
  }

  fun testNegativeDouble() {
    doHighlightTest()
  }
}
