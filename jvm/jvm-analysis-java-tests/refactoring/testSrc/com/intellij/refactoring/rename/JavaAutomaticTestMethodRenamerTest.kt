// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.jvmAutomaticTestMethodRenamerFixture
import com.intellij.jvm.analysis.testFramework.jvmProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class JavaAutomaticTestMethodRenamerTest {
  companion object {
    private val projectFixture = jvmProjectFixture()
  }

  private val myFixture by jvmAutomaticTestMethodRenamerFixture(projectFixture, JavaJvmAnalysisTestUtil.testDataPath + "/refactoring/renameTestMethod")

  @Test
  fun testTestMethod() = myFixture.doTest("ClassTest.java")

  @Test
  fun testHelperMethod() = myFixture.doTest("ClassTest.java")

  @Test
  fun testHelperClass() = myFixture.doTest("TestUtil.java")
}