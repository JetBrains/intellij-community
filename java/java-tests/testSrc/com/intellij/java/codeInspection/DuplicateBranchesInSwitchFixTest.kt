// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase
import com.intellij.codeInspection.DuplicateBranchesInSwitchInspection
import com.intellij.codeInspection.LocalInspectionTool

/**
 * @author Pavel.Dolgov
 */
class DuplicateBranchesInSwitchFixTest : LightQuickFixParameterizedTestCase() {

  override fun configureLocalInspectionTools(): Array<LocalInspectionTool> = arrayOf(DuplicateBranchesInSwitchInspection())

  override fun getBasePath() = "/inspection/duplicateBranchesInSwitchFix"
}