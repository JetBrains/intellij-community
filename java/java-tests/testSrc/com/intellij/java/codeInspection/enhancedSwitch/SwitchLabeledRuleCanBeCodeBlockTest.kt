// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.enhancedSwitch

import com.intellij.JavaTestUtil
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.enhancedSwitch.SwitchLabeledRuleCanBeCodeBlockInspection
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */
class SwitchLabeledRuleCanBeCodeBlockTest : LightCodeInsightFixtureTestCase() {
  val inspection = SwitchLabeledRuleCanBeCodeBlockInspection()

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)

    val currentProfile = ProjectInspectionProfileManager.getInstance(project).currentProfile
    currentProfile.setErrorLevel(HighlightDisplayKey.find(inspection.shortName), HighlightDisplayLevel.WARNING, project)
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = LightCodeInsightFixtureTestCase.JAVA_12

  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/switchLabeledRuleCanBeCodeBlock"

  fun testInExpression() = doTest()
  fun testInStatement() = doTest()

  private fun doTest() {
    myFixture.testHighlighting("${getTestName(false)}.java")
  }
}