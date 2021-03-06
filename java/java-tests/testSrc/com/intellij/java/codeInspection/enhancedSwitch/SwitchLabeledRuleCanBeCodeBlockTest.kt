// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.enhancedSwitch

import com.intellij.JavaTestUtil
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.enhancedSwitch.SwitchLabeledRuleCanBeCodeBlockInspection
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */
class SwitchLabeledRuleCanBeCodeBlockTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/switchLabeledRuleCanBeCodeBlock"

  fun testInExpression() = doTest()
  fun testInStatement() = doTest()

  private fun doTest() {
    val inspection = SwitchLabeledRuleCanBeCodeBlockInspection()
    myFixture.enableInspections(inspection)
    val currentProfile = ProjectInspectionProfileManager.getInstance(project).currentProfile
    currentProfile.setErrorLevel(HighlightDisplayKey.find(inspection.shortName), HighlightDisplayLevel.WARNING, project)
    myFixture.testHighlighting("${getTestName(false)}.java")
  }
}