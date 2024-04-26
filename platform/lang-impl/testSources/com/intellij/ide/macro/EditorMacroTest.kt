// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.macro

import com.intellij.openapi.application.ReadAction
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class EditorMacroTest: LightPlatformCodeInsightTestCase() {
  fun testSelectionPositionMacros() {
    configureFromFileText("test.txt", "      first line    \n" +
                                                      "      <selection>second line</selection>     ")
    val start = editor.offsetToLogicalPosition(editor.selectionModel.selectionStart)
    assertEquals((start.line + 1).toString(), expand(SelectionStartLineMacro()))
    assertEquals((start.column + 1).toString(), expand(SelectionStartColumnMacro()))

    val end = editor.offsetToLogicalPosition(editor.selectionModel.selectionEnd)
    assertEquals((end.line + 1).toString(), expand(SelectionEndLineMacro()))
    assertEquals((end.column + 1).toString(), expand(SelectionEndColumnMacro()))
  }

  private fun expand(macro: EditorMacro) = runBlocking(Dispatchers.IO) {
    ReadAction.compute<String?, Exception> { macro.expand(editor) }
  }
}