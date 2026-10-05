// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs the git tasks of the push dialog. The disposal of the dialog cancels them and stops their git commands.
 */
@Service(Service.Level.PROJECT)
internal class PushDialogTasks(private val project: Project, private val coroutineScope: CoroutineScope) {
  // the remote checks use the network, so a project with many repositories doesn't start all of them at once
  private val backgroundDispatcher = Dispatchers.IO.limitedParallelism(4)

  /**
   * Runs [task] under modal progress above the push dialog, then runs [onFinished] on the EDT, also after a cancellation.
   * The disposal of [parentDisposable] cancels the task.
   */
  @RequiresEdt
  fun runWithModalProgress(parentDisposable: Disposable, title: @NlsContexts.ModalProgressTitle String, task: () -> Unit, onFinished: () -> Unit) {
    coroutineScope.launch(Dispatchers.EDT + ModalityState.current().asContextElement()) {
      try {
        withModalProgress(project, title) {
          // the progress keeps the EDT dispatcher of the caller, and the task runs git
          withContext(Dispatchers.IO) {
            coroutineToIndicator { task() }
          }
        }
      }
      finally {
        withContext(NonCancellable) { onFinished() }
      }
    }.cancelOnDispose(parentDisposable)
  }

  /**
   * Runs [task] on a background thread, then passes its result to [onFinished] on the EDT.
   * The disposal of [parentDisposable] cancels the task. A canceled task doesn't call [onFinished].
   *
   * @return the job of the task, to cancel it when its result is no longer needed
   */
  @RequiresEdt
  fun <T> runInBackground(parentDisposable: Disposable, task: () -> T, onFinished: (T) -> Unit): Job {
    val job = coroutineScope.launch(Dispatchers.EDT + ModalityState.current().asContextElement()) {
      val result = withContext(backgroundDispatcher) {
        coroutineToIndicator { task() }
      }
      onFinished(result)
    }
    job.cancelOnDispose(parentDisposable)
    return job
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PushDialogTasks = project.service()
  }
}
