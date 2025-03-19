package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.SameParameterValueInspectionTestBase
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinSameParameterValueLocalInspectionTest : SameParameterValueInspectionTestBase(true), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

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
