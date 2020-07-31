// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.enhancedSwitch

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.enhancedSwitch.SwitchLabeledRuleCanBeCodeBlockInspection

/**
 * @author Pavel.Dolgov
 */
class SwitchLabeledRuleCanBeCodeBlockFixTest : LightQuickFixParameterizedTestCase() {

  override fun configureLocalInspectionTools(): Array<LocalInspectionTool> = arrayOf(SwitchLabeledRuleCanBeCodeBlockInspection())
  
  override fun getBasePath() = "/inspection/switchLabeledRuleCanBeCodeBlockFix"
}
