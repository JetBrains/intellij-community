// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.DumbModeBlockedFunctionality
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SimpleModificationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class MockDumbService(override val project: Project) : DumbService() {
  override val modificationTracker: ModificationTracker
    get() = SimpleModificationTracker()

  override val isDumb: Boolean = false
  override val state: StateFlow<DumbState> = MutableStateFlow(object : DumbState {
    override val isDumb: Boolean = false
  })
  override val dumbModeStartTrace: Throwable? = null

  override var isAlternativeResolveEnabled: Boolean
    get() = false
    set(enabled) {}

  override fun canRunSmart(): Boolean = true

  override fun runWhenSmart(runnable: Runnable) {
    runnable.run()
  }

  override fun waitForSmartMode() {}

  override fun waitForSmartMode(timeoutMillis: Long): Boolean = true

  override fun queueTask(task: DumbModeTask) {
    task.performInDumbMode(EmptyProgressIndicator())
    Disposer.dispose(task)
  }

  override fun cancelTask(task: DumbModeTask) {}

  override fun cancelAllTasksAndWait() {}

  override fun completeJustSubmittedTasks() {}

  override fun wrapGently(dumbUnawareContent: JComponent, parentDisposable: Disposable): JComponent =
    throw UnsupportedOperationException()

  override fun wrapWithSpoiler(dumbAwareContent: JComponent, updateRunnable: Runnable, parentDisposable: Disposable): JComponent =
    dumbAwareContent

  override fun showDumbModeNotification(message: String) {
    throw UnsupportedOperationException()
  }

  override fun showDumbModeNotificationForFunctionality(
    message: @NlsContexts.PopupContent String,
    functionality: DumbModeBlockedFunctionality,
  ) {
    throw UnsupportedOperationException()
  }

  override fun showDumbModeNotificationForFunctionalityWithCoalescing(message: String, functionality: DumbModeBlockedFunctionality, equality: Any) {
    throw UnsupportedOperationException()
  }

  override fun showDumbModeNotificationForAction(message: @NlsContexts.PopupContent String, actionId: String?) {
    throw UnsupportedOperationException()
  }

  override fun showDumbModeNotificationForFailedAction(message: String, actionId: String?) {
    throw UnsupportedOperationException()
  }

  override fun showDumbModeActionBalloon(
    balloonText: @NlsContexts.PopupContent String,
    runWhenSmartAndBalloonStillShowing: Runnable,
    actionIds: List<String>,
  ) {
    throw UnsupportedOperationException()
  }

  override fun suspendIndexingAndRun(activityName: String, activity: Runnable) {
    activity.run()
  }

  override suspend fun suspendIndexingAndRun(activityName: String, activity: suspend CoroutineScope.() -> Unit) {
    coroutineScope {
      activity()
    }
  }

  override fun unsafeRunWhenSmart(runnable: Runnable) {
    runnable.run()
  }

  override fun smartInvokeLater(runnable: Runnable) {
    runnable.run()
  }

  override fun smartInvokeLater(runnable: Runnable, modalityState: ModalityState) {
    runnable.run()
  }

  override fun runWithWaitForSmartModeDisabled(): AccessToken = AccessToken.EMPTY_ACCESS_TOKEN

  override suspend fun <T> runInDumbMode(debugReason: String, block: suspend () -> T): T = block()
}
