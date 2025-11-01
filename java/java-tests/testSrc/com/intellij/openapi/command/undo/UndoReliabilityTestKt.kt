// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.undo

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.testFramework.executeSomeCoroutineTasksAndDispatchAllInvocationEvents
import kotlinx.coroutines.*
import kotlin.coroutines.EmptyCoroutineContext


class UndoReliabilityTestKt : ComplexUndoTest() {

  fun `test undo is valid if cancellation happens inside a command`() {
    newCoroutineScope().launch(Dispatchers.EDT) {
      runUndoTransparentWriteAction {
        firstEditor.document.insertString(0, "A")
        coroutineContext.cancel()
        firstEditor.document.insertString(1, "B")
      }
    }
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents(myProject)
    checkEditorText("AB")
    assertUndoInFirstEditorIsAvailable()
    undoFirstEditor()
    checkEditorText("")
  }

  private fun newCoroutineScope(): CoroutineScope {
    return CoroutineScope(EmptyCoroutineContext)
  }
}
