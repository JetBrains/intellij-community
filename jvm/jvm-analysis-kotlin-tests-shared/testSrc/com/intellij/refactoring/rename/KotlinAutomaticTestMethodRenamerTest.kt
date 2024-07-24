// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.jvm.analysis.testFramework.AutomaticTestMethodRenamerTestBase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinAutomaticTestMethodRenamerTest : AutomaticTestMethodRenamerTestBase(), KotlinPluginModeProvider {
    override fun getBasePath(): String = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/refactoring/renameTestMethod"

    fun testTestMethod() = doTest("ClassTest.kt")

    fun testHelperMethod() = doTest("ClassTest.kt")

    fun testHelperClass() = doTest("TestUtil.kt")
}