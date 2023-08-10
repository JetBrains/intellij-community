// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions

import com.intellij.openapi.editor.actions.TransposeAction
import com.intellij.testFramework.LightPlatformCodeInsightTestCase

// tests for rotation only, other tests can be found in com.intellij.openapi.editor.actions.TransposeTest
class TransposeTest : LightPlatformCodeInsightTestCase() {

  fun `test inline forward rotation`() {
    doTest("json")
  }

  fun `test multiline forward rotation`() {
    doTest("json")
  }

  private fun doTest(extension: String) {
    val dir = "/codeInsight/rotateSelections"
    val beforeFile = dir + "/" + getTestName(false).removePrefix(" ").replace(' ', '_') + "." + extension
    configureByFile(beforeFile)
    TransposeAction().handler.execute(editor, null, null)
    val afterFile = dir + "/" + getTestName(false).removePrefix(" ").replace(' ', '_') + "_after" + "." + extension
    checkResultByFile(afterFile)
  }
}