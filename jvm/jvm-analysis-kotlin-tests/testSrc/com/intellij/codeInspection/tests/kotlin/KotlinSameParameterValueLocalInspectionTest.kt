package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.SameParameterValueInspectionTestBase

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

  fun testReceiver() {
    doHighlightTest()
  }
}
