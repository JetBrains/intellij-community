// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.undo

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.executeSomeCoroutineTasksAndDispatchAllInvocationEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.EmptyCoroutineContext

class UndoReliabilityTestKt : EditorUndoTestCase() {

  fun `test undo is valid if cancellation happens inside a command`() {
    newCoroutineScope().launch(Dispatchers.EDT) {
      runUndoTransparentWriteAction {
        firstEditor.document.insertString(0, "A")
        coroutineContext.cancel()
        firstEditor.document.insertString(1, "B")
      }
    }
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents()
    checkEditorText("AB")
    assertUndoInFirstEditorIsAvailable()
    undoFirstEditor()
    checkEditorText("")
  }

  fun `test undo is valid if cancellation happens during command start`() {
    newCoroutineScope().launch(Dispatchers.EDT) {
      val cancellationInjected = AtomicBoolean(false)
      val cancellationDuringCommandStart = object : CurrentEditorProvider {
        override fun getCurrentEditor(project: Project?): FileEditor? {
          if (cancellationInjected.compareAndSet(false, true)) {
            coroutineContext.cancel()
          }
          ProgressManager.checkCanceled()
          return getFileEditor(firstEditor)
        }
      }
      withEditorProvider(cancellationDuringCommandStart) {
        CommandProcessor.getInstance().executeCommand(myProject, {
          runWriteActionAfterCancellationInjected(cancellationInjected) {
            firstEditor.document.insertString(0, "A")
          }
        }, "test", null)
      }
    }
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents()
    checkEditorText("A")
    assertUndoInFirstEditorIsAvailable()
    undoFirstEditor()
    checkEditorText("")
  }

  private fun runWriteActionAfterCancellationInjected(cancellationInjected: AtomicBoolean, action: () -> Unit) {
    check(cancellationInjected.get()) {
      "cancellation was not injected during command start"
    }
    ProgressManager.getInstance().executeNonCancelableSection {
      ApplicationManager.getApplication().runWriteAction { action.invoke() }
    }
  }

  private fun withEditorProvider(editorProvider: CurrentEditorProvider, task: () -> Unit) {
    val globalUndoManager = UndoManager.getGlobalInstance() as UndoManagerImpl
    globalUndoManager.setOverriddenEditorProvider(editorProvider)
    myManager.setOverriddenEditorProvider(editorProvider)
    try {
      task.invoke()
    } finally {
      globalUndoManager.setOverriddenEditorProvider(null)
      myManager.setOverriddenEditorProvider(null)
    }
  }

  private fun newCoroutineScope(): CoroutineScope {
    @Suppress("RAW_SCOPE_CREATION")
    return CoroutineScope(EmptyCoroutineContext)
  }
}
