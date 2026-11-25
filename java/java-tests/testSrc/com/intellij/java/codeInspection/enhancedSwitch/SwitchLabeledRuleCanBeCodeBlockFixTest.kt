// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.enhancedSwitch

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.quickFix.lightQuickFixFixture
import com.intellij.codeInsight.daemon.quickFix.lightQuickFixTestFiles
import com.intellij.codeInspection.enhancedSwitch.SwitchLabeledRuleCanBeCodeBlockInspection
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.fixtures.lightJavaTestDescriptor
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.function.Supplier

@TestApplication
class SwitchLabeledRuleCanBeCodeBlockFixTest {
  companion object {
    private val TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/inspection/switchLabeledRuleCanBeCodeBlockFix"
    private val projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor())

    @JvmStatic
    fun files(): List<String> = lightQuickFixTestFiles(TEST_DATA_PATH)
  }

  private val quickFixFixture =
    lightQuickFixFixture(projectFixture, TEST_DATA_PATH, Supplier { listOf(SwitchLabeledRuleCanBeCodeBlockInspection()) })

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  fun parameterized(fileName: String) {
    quickFixFixture.get().doTest(fileName)
  }
}