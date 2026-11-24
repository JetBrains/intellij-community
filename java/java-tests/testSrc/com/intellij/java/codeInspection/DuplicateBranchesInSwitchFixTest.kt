// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFileBasedTest
import com.intellij.codeInspection.DuplicateBranchesInSwitchInspection
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach

@TestApplication
class DuplicateBranchesInSwitchFixTest : LightQuickFixFileBasedTest() {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_21)
  }

  private val codeInsightFixture = lightJavaCodeInsightFixture(projectFixture)

  override fun getFixture(): JavaCodeInsightTestFixture = codeInsightFixture.get()

  override fun getTestDataPath(): String = JavaTestUtil.getJavaTestDataPath() + "/inspection/duplicateBranchesInSwitchFix"

  @BeforeEach
  fun setUpInspections() {
    fixture.enableInspections(DuplicateBranchesInSwitchInspection())
  }
}