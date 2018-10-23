// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.duplicateExpressions.DuplicateExpressionsInspection
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */
class DuplicateExpressionsTest : LightCodeInsightFixtureTestCase() {
  val inspection = DuplicateExpressionsInspection()

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)
  }

  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/duplicateExpressions"

  fun testUpdateInWhileLoop() = doTest(70)
  fun testIntFloatMixedMath() = doTest(50)
  fun testTernaryIf() = doTest(60)
  fun testStringStartsWith() = doTest(60)
  fun testFinalFieldsOfParameter() = doTest(100)
  fun testStringCharAt() = doTest(60)
  fun testEquals() = doTest(70)
  fun testVariableModified() = doTest(50)
  fun testVariableNotModified() = doTest(50)
  fun testCompositeQualifier() = doTest(40)
  fun testMethodCallWithSideEffect() = doTest(70)

  private fun doTest(threshold: Int = 50) {
    val oldThreshold = inspection.complexityThreshold
    try {
      inspection.complexityThreshold = threshold
      myFixture.testHighlighting("${getTestName(false)}.java")
    }
    finally {
      inspection.complexityThreshold = oldThreshold
    }
  }
}
