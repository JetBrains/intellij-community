// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.enhancedSwitch

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.enhancedSwitch.RedundantLabeledSwitchRuleCodeBlockInspection

/**
 * @author Pavel.Dolgov
 */
class RedundantLabeledSwitchRuleCodeBlockFixTest  : LightQuickFixParameterizedTestCase() {
  override fun configureLocalInspectionTools(): Array<LocalInspectionTool> = arrayOf(RedundantLabeledSwitchRuleCodeBlockInspection())


  override fun getBasePath() = "/inspection/redundantLabeledSwitchRuleCodeBlockFix"
}