// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.enhancedSwitch

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFileBasedTest
import com.intellij.codeInspection.enhancedSwitch.RedundantLabeledSwitchRuleCodeBlockInspection
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.fixtures.lightJavaTestDescriptor
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach

@TestApplication
class RedundantLabeledSwitchRuleCodeBlockFixTest : LightQuickFixFileBasedTest() {
  companion object {
    private val projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor())
  }

  private val codeInsightFixture = lightJavaCodeInsightFixture(projectFixture)

  override fun getFixture(): JavaCodeInsightTestFixture = codeInsightFixture.get()

  override fun getTestDataPath(): String = JavaTestUtil.getJavaTestDataPath() + "/inspection/redundantLabeledSwitchRuleCodeBlockFix"

  @BeforeEach
  fun setUpInspections() {
    fixture.enableInspections(RedundantLabeledSwitchRuleCodeBlockInspection())
  }
}