// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.ui

import com.intellij.model.Pointer
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.Command
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.api.RenameTarget
import kotlinx.coroutines.*
import java.util.concurrent.locks.LockSupport
import kotlin.coroutines.ContinuationInterceptor

internal val uiDispatcher: ContinuationInterceptor = AppUIExecutor.onUiThread(ModalityState.NON_MODAL).coroutineDispatchingContext()

/**
 * Shows a background progress indicator in the UI,
 * the indicator cancels the coroutine when cancelled from the UI.
 */
internal suspend fun <T> withBackgroundIndicator(
  project: Project,
  @ProgressTitle progressTitle: String,
  action: suspend CoroutineScope.() -> T
): T = coroutineScope {
  if (ApplicationManager.getApplication().isUnitTestMode) {
    return@coroutineScope action()
  }
  val deferred = async(block = action)
  launch(Dispatchers.IO) { // block some thread while [action] is not completed
    CoroutineBackgroundTask(project, progressTitle, deferred).queue()
  }
  deferred.await()
}

/**
 * - stays in progress while the [job] is running;
 * - cancels the [job] then user cancels the progress in the UI.
 */
private class CoroutineBackgroundTask(
  project: Project,
  @ProgressTitle progressTitle: String,
  private val job: Job
) : Task.Backgroundable(project, progressTitle) {

  override fun run(indicator: ProgressIndicator) {
    while (job.isActive) {
      if (indicator.isCanceled) {
        job.cancel()
        return
      }
      LockSupport.parkNanos(10_000_000)
    }
  }
}

private suspend fun Pointer<out RenameTarget>.presentableText(): String? {
  return readAction {
    dereference()?.presentation?.presentableText
  }
}

@ProgressTitle
internal suspend fun Pointer<out RenameTarget>.progressTitle(): String? {
  return presentableText()?.let { presentableText ->
    RefactoringBundle.message("rename.progress.title.0", presentableText)
  }
}

@Command
internal suspend fun Pointer<out RenameTarget>.commandName(newName: String): String? {
  return presentableText()?.let { presentableText ->
    RefactoringBundle.message("rename.command.name.0.1", presentableText, newName)
  }
}
