// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.undo

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.impl.UndoProvider
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.executeSomeCoroutineTasksAndDispatchAllInvocationEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

  fun `test undo is valid if cancellation happens before command start`() {
    forceCurrentEditorProviderService()
    newCoroutineScope().launch(Dispatchers.EDT) {
      val cancellationBeforeCommandStart = object : UndoProvider {
        override fun commandStarted(project: Project?) = coroutineContext.cancel()
        override fun commandFinished(project: Project?) = Unit
      }
      withUndoProvider(cancellationBeforeCommandStart) {
        CommandProcessor.getInstance().runUndoTransparentAction {
          // cancellation happens here inside the undo manager
          runWriteActionWithoutCancellationCheckOnLockAcquiring {
            firstEditor.document.insertString(0, "A")
          }
        }
      }
    }
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents()
    checkEditorText("A")
    assertUndoInFirstEditorIsAvailable()
    undoFirstEditor()
    checkEditorText("")
  }

  /**
   * Force running write action within a cancelled coroutine
   */
  private fun runWriteActionWithoutCancellationCheckOnLockAcquiring(alreadyCanceledTask: () -> Unit) {
    require(isCancelableSection())
    val suppressCancellationCheckInWriteLockAcquiring = Cancellation.withNonCancelableSection()
    var cancellationTokenClosed = false
    try {
      ApplicationManager.getApplication().runWriteAction {
        suppressCancellationCheckInWriteLockAcquiring.finish()
        cancellationTokenClosed = true
        check(isCancelableSection()) {
          "non cancelable section is not finished as expected"
        }
        alreadyCanceledTask.invoke()
      }
    } finally {
      if (!cancellationTokenClosed) {
        suppressCancellationCheckInWriteLockAcquiring.finish()
      }
    }
  }

  /**
   * Force `CurrentEditorProvider.getInstance()` in order to match the production behavior
   */
  private fun forceCurrentEditorProviderService() {
    (UndoManager.getGlobalInstance() as UndoManagerImpl).setOverriddenEditorProvider(null)
    myManager.setOverriddenEditorProvider(null)
  }

  private fun withUndoProvider(undoProvider: UndoProvider, task: () -> Unit) {
    val disposable = Disposer.newDisposable()
    UndoProvider.EP_NAME.point.registerExtension(undoProvider, disposable)
    UndoProvider.PROJECT_EP_NAME.getPoint(myProject).registerExtension(undoProvider, disposable)
    try {
      task.invoke()
    } finally {
      Disposer.dispose(disposable)
    }
  }

  private fun isCancelableSection(): Boolean {
    return !Cancellation.isInNonCancelableSection()
  }

  private fun newCoroutineScope(): CoroutineScope {
    @Suppress("RAW_SCOPE_CREATION")
    return CoroutineScope(EmptyCoroutineContext)
  }
}
