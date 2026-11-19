package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.sameParameterValueInspectionFixture
import com.intellij.testFramework.javaInspectionProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
abstract class KotlinSameParameterValueGlobalInspectionTest {
  companion object {
    private val projectFixture = javaInspectionProjectFixture()
  }

  protected val sameParameterValue by sameParameterValueInspectionFixture(projectFixture, isLocal = false)

  @Test
  fun testEntryPoint() {
    sameParameterValue.doHighlightTest(runDeadCodeFirst = true)
  }

  @Test
  fun testMethodWithSuper() {
    sameParameterValue.doHighlightTest()
  }

  @Test
  fun testVarargs() {
    sameParameterValue.doHighlightTest()
  }

  @Test
  fun testNamedArg() {
    sameParameterValue.doHighlightTest()
  }

  @Test
  fun testNegativeDouble() {
    sameParameterValue.doHighlightTest()
  }

  @Test
  fun testReceiver() {
    sameParameterValue.doHighlightTest()
  }
}
