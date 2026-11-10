// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.duplicateExpressions.DuplicateExpressionsInspection
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class DuplicateExpressionsTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/inspection/duplicateExpressions"
  }

  val inspection = DuplicateExpressionsInspection()

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(inspection)
  }

  @Test
  fun testUpdateInWhileLoop(): Unit = doTest(70)
  @Test
  fun testIntFloatMixedMath(): Unit = doTest(50)
  @Test
  fun testTernaryIf(): Unit = doTest(60)
  @Test
  fun testStringStartsWith(): Unit = doTest(60)
  @Test
  fun testFinalFieldsOfParameter(): Unit = doTest(100)
  @Test
  fun testStringCharAt(): Unit = doTest(60)
  @Test
  fun testEquals(): Unit = doTest(70)
  @Test
  fun testVariableModified(): Unit = doTest(50)
  @Test
  fun testVariableNotModified(): Unit = doTest(50)
  @Test
  fun testCompositeQualifier(): Unit = doTest(40)
  @Test
  fun testMethodCallWithSideEffect(): Unit = doTest(70)
  @Test
  fun testMathSin(): Unit = doTest(25)
  @Test
  fun testMathMax(): Unit = doTest(45)
  @Test
  fun testMathRandom(): Unit = doTest(1)
  @Test
  fun testVariable(): Unit = doTest(1)
  @Test
  fun testLambda(): Unit = doTest(20)
  @Test
  fun testFile(): Unit = doTest(1)
  @Test
  fun testCollections(): Unit = doTest(1)
  @Test
  fun testDeepNestedClass(): Unit = doTest(7)
  @Test
  fun testQualifier(): Unit = doTest(1)
  @Test
  fun testComplexPackage(): Unit = doTest(pkg="foo/bar/baz/")

  private fun doTest(threshold: Int = 50, pkg: String = "") {
    val oldThreshold = inspection.complexityThreshold
    try {
      inspection.complexityThreshold = threshold
      myFixture.testHighlighting("$pkg${myFixture.getTestName(false)}.java")
    }
    finally {
      inspection.complexityThreshold = oldThreshold
    }
  }
}
