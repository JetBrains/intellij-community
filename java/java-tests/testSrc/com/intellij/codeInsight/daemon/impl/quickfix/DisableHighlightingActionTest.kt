// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.quickFix.ActionHint
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.testFramework.runInInitMode
import org.jetbrains.annotations.NonNls

class DisableHighlightingActionTest : LightQuickFixParameterizedTestCase() {
  private var myKey: HighlightDisplayKey? = null
  override fun configureLocalInspectionTools(): Array<LocalInspectionTool> {
    return arrayOf(
      SillyAssignmentInspection()
    )
  }

  override fun setUp() {
    super.setUp()
    myKey = HighlightDisplayKey.find(SillyAssignmentInspection().shortName)
  }

  override fun getBasePath(): @NonNls String {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/disableHighlighting"
  }

  override fun doAction(actionHint: ActionHint, testFullPath: String, testName: String) {
    //ensure action which access inspection profile can do that
    runInInitMode {
      super.doAction(actionHint, testFullPath, testName)
    }
    assertEquals(HighlightDisplayLevel.DO_NOT_SHOW,
                 InspectionProjectProfileManager.getInstance(project).currentProfile.getErrorLevel(myKey!!, null))
  }
}