package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.shared.testFramework.SameParameterValueInspectionTestBase

class KotlinSameParameterValueGlobalInspectionTest : SameParameterValueInspectionTestBase(false) {
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
