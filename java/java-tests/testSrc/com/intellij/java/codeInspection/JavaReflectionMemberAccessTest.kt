// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.reflectiveAccess.JavaReflectionMemberAccessInspection
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class JavaReflectionMemberAccessTest : LightJavaCodeInsightFixtureTestCase() {

  private val inspection = JavaReflectionMemberAccessInspection()

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_8 // older mock JREs are missing some bits

  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/javaReflectionMemberAccess"

  fun testFields() = doTest()
  fun testMethods() = doTest()
  fun testConstructors() = doTest()

  fun testFieldExists() = doTest(true)
  fun testMethodExists() = doTest(true)
  fun testMethodInnerClass() = doTest(true)
  fun testConstructorExists() = doTest(true)
  fun testConstructorInnerClass() = doTest(true)
  fun testConstructorNestedInnerClass() = doTest(true)
  fun testConstructorLocalClass() = doTest(true)
  fun testConstructorLocalClassInStaticMethod() = doTest(true)

  fun testNewInstance() = doTest(true)
  fun testBugs() = doTest(true)
  fun testClassArray() = doTest(true)


  private fun doTest(checkExists: Boolean = false) {
    inspection.checkMemberExistsInNonFinalClasses = checkExists
    myFixture.testHighlighting("${getTestName(false)}.java")
  }
}