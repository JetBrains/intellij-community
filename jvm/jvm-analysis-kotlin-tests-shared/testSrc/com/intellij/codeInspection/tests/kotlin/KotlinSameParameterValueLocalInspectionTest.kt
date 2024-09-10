package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.SameParameterValueInspectionTestBase
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider

abstract class KotlinSameParameterValueLocalInspectionTest : SameParameterValueInspectionTestBase(true), ExpectedPluginModeProvider {
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
