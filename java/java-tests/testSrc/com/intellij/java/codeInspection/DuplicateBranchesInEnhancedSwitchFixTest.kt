// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.quickFix.lightQuickFixFixture
import com.intellij.codeInsight.daemon.quickFix.lightQuickFixTestFiles
import com.intellij.codeInspection.DuplicateBranchesInSwitchInspection
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.function.Supplier

@TestApplication
class DuplicateBranchesInEnhancedSwitchFixTest {
  companion object {
    private val TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/inspection/duplicateBranchesInEnhancedSwitchFix"
    private val projectFixture = lightJavaProjectFixture(JAVA_21)

    @JvmStatic
    fun files(): List<String> = lightQuickFixTestFiles(TEST_DATA_PATH)
  }

  private val quickFixFixture =
    lightQuickFixFixture(projectFixture, TEST_DATA_PATH, Supplier { listOf(DuplicateBranchesInSwitchInspection()) })

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  fun parameterized(fileName: String) {
    quickFixFixture.get().doTest(fileName)
  }
}
