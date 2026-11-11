// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.reflectiveAccess.JavaReflectionMemberAccessInspection
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class JavaReflectionMemberAccessTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8) // older mock JREs are missing some bits
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  private val inspection = JavaReflectionMemberAccessInspection()

  @BeforeEach
  fun setUp() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/inspection/javaReflectionMemberAccess"
    myFixture.enableInspections(inspection)
  }

  @Test
  fun testFields(): Unit = doTest()
  @Test
  fun testMethods(): Unit = doTest()
  @Test
  fun testConstructors(): Unit = doTest()

  @Test
  fun testFieldExists(): Unit = doTest(true)
  @Test
  fun testMethodExists(): Unit = doTest(true)
  @Test
  fun testMethodInnerClass(): Unit = doTest(true)
  @Test
  fun testConstructorExists(): Unit = doTest(true)
  @Test
  fun testConstructorInnerClass(): Unit = doTest(true)
  @Test
  fun testConstructorNestedInnerClass(): Unit = doTest(true)
  @Test
  fun testConstructorLocalClass(): Unit = doTest(true)
  @Test
  fun testConstructorLocalClassInStaticMethod(): Unit = doTest(true)

  @Test
  fun testNewInstance(): Unit = doTest(true)
  @Test
  fun testBugs(): Unit = doTest(true)
  @Test
  fun testClassArray(): Unit = doTest(true)


  private fun doTest(checkExists: Boolean = false) {
    inspection.checkMemberExistsInNonFinalClasses = checkExists
    myFixture.testHighlighting("${myFixture.getTestName(false)}.java")
  }
}