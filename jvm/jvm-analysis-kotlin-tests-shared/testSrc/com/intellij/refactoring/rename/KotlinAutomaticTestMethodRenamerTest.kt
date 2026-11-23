// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.jvmAutomaticTestMethodRenamerFixture
import com.intellij.jvm.analysis.testFramework.jvmProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
abstract class KotlinAutomaticTestMethodRenamerTest {
  companion object {
    private val projectFixture = jvmProjectFixture()
  }

  protected val myFixture by jvmAutomaticTestMethodRenamerFixture(projectFixture, KotlinJvmAnalysisTestUtil.testDataPath + "/refactoring/renameTestMethod")

    @Test
    fun testTestMethod() = myFixture.doTest("ClassTest.kt")

    @Test
    fun testHelperMethod() = myFixture.doTest("ClassTest.kt")

    @Test
    fun testHelperClass() = myFixture.doTest("TestUtil.kt")
}