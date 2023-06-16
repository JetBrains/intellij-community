package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.SameParameterValueInspectionTestBase

class KotlinSameParameterValueLocalInspectionTest : SameParameterValueInspectionTestBase(true) {
  fun testEntryPoint() {
    doHighlightTest(runDeadCodeFirst = true)
  }

  fun testMethodWithSuper() {
    doHighlightTest()
  }

  fun testVarargs() {
    doHighlightTest()
  }

  fun testNamedArg() {
    doHighlightTest()
  }

  fun testNegativeDouble() {
    doHighlightTest()
  }
}
