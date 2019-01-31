// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.highlighting

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */
class AtomicReferenceImplicitUsageTest : LightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = JAVA_8
  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/atomicReferenceImplicitUsage/"

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(UnusedDeclarationInspection())
  }

  fun testReferenceGet() = doTest()
  fun testIntegerGet() = doTest()
  fun testLongGet() = doTest()

  fun testReferenceCAS() = doTest()
  fun testIntegerCAS() = doTest()
  fun testLongCAS() = doTest()

  fun testReferenceGetAndSet() = doTest()
  fun testIntegerGetAndSet() = doTest()
  fun testLongGetAndSet() = doTest()

  fun testReferenceSet() = doTest()
  fun testIntegerSet() = doTest()
  fun testLongSet() = doTest()

  fun testIntegerIncrement() = doTest()
  fun testLongIncrement() = doTest()

  fun testNonVolatile() = doTest()

  private fun doTest() {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }
}