// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase
import com.intellij.codeInspection.DuplicateBranchesInSwitchInspection
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */
class DuplicateBranchesInEnhancedSwitchFixTest : LightQuickFixParameterizedTestCase() {

  override fun configureLocalInspectionTools(): Array<LocalInspectionTool> = arrayOf(DuplicateBranchesInSwitchInspection())

  override fun getBasePath() = "/inspection/duplicateBranchesInEnhancedSwitchFix"

  override fun getProjectDescriptor(): LightProjectDescriptor = LightCodeInsightFixtureTestCase.JAVA_12
}