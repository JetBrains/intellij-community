// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions

import com.intellij.codeInsight.editorActions.rotateSelection.RotateSelectionsBackwardsAction
import com.intellij.codeInsight.editorActions.rotateSelection.RotateSelectionsForwardAction
import com.intellij.testFramework.LightPlatformCodeInsightTestCase

class RotateSelectionsHandlerTest : LightPlatformCodeInsightTestCase() {

  fun `test inline forward rotation`() {
    doTest(true, "json")
  }

  fun `test multiline forward rotation`() {
    doTest(true, "json")
  }

  fun `test forward rotation with caret without selection`() {
    doTest(true, "html")
  }

  fun `test inline backwards rotation`() {
    doTest(false, "json")
  }

  fun `test multiline backwards rotation`() {
    doTest(false, "json")
  }

  private fun doTest(isForward: Boolean, extension: String) {
    val dir = "/codeInsight/rotateSelections"
    val beforeFile = dir + "/" + getTestName(false).removePrefix(" ").replace(' ', '_') + "." + extension
    configureByFile(beforeFile)
    if (isForward) {
      RotateSelectionsForwardAction().handler.execute(editor, null, null)
    } else {
      RotateSelectionsBackwardsAction().handler.execute(editor, null, null)
    }
    val afterFile = dir + "/" + getTestName(false).removePrefix(" ").replace(' ', '_') + "_after" + "." + extension
    checkResultByFile(afterFile)
  }
}