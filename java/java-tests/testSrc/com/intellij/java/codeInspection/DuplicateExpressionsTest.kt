// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.duplicateExpressions.DuplicateExpressionsInspection
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */
class DuplicateExpressionsTest : LightJavaCodeInsightFixtureTestCase() {
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
  fun testMathSin() = doTest(25)
  fun testMathMax() = doTest(45)
  fun testMathRandom() = doTest(1)
  fun testVariable() = doTest(1)
  fun testLambda() = doTest(20)
  fun testFile() = doTest(1)
  fun testCollections() = doTest(1)
  fun testDeepNestedClass() = doTest(7)
  fun testQualifier() = doTest(1)
  fun testComplexPackage() = doTest(pkg="foo/bar/baz/")

  private fun doTest(threshold: Int = 50, pkg: String = "") {
    val oldThreshold = inspection.complexityThreshold
    try {
      inspection.complexityThreshold = threshold
      myFixture.testHighlighting("$pkg${getTestName(false)}.java")
    }
    finally {
      inspection.complexityThreshold = oldThreshold
    }
  }
}
