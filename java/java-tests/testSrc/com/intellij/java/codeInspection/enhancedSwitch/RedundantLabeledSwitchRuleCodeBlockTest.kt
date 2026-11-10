// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.enhancedSwitch

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.enhancedSwitch.RedundantLabeledSwitchRuleCodeBlockInspection
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class RedundantLabeledSwitchRuleCodeBlockTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/inspection/redundantLabeledSwitchRuleCodeBlock"
  }

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(
      RedundantLabeledSwitchRuleCodeBlockInspection())
  }

  @Test
  fun testInExpression(): Unit = doTest()
  @Test
  fun testInStatement(): Unit = doTest()

  private fun doTest() {
    myFixture.testHighlighting("${myFixture.getTestName(false)}.java")
  }
}