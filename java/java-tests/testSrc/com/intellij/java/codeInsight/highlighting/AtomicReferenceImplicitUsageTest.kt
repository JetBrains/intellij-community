// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.highlighting

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_8
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class AtomicReferenceImplicitUsageTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_8)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/atomicReferenceImplicitUsage/"
  }

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(UnusedDeclarationInspection())
  }

  @Test
  fun testReferenceGet(): Unit = doTest()
  @Test
  fun testIntegerGet(): Unit = doTest()
  @Test
  fun testLongGet(): Unit = doTest()

  @Test
  fun testReferenceCAS(): Unit = doTest()
  @Test
  fun testIntegerCAS(): Unit = doTest()
  @Test
  fun testLongCAS(): Unit = doTest()

  @Test
  fun testReferenceGetAndSet(): Unit = doTest()
  @Test
  fun testIntegerGetAndSet(): Unit = doTest()
  @Test
  fun testLongGetAndSet(): Unit = doTest()

  @Test
  fun testReferenceSet(): Unit = doTest()
  @Test
  fun testIntegerSet(): Unit = doTest()
  @Test
  fun testLongSet(): Unit = doTest()

  @Test
  fun testIntegerIncrement(): Unit = doTest()
  @Test
  fun testLongIncrement(): Unit = doTest()

  @Test
  fun testNonVolatile(): Unit = doTest()

  private fun doTest() {
    myFixture.configureByFile(myFixture.getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }
}