// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.diagnostic.dumpCoroutines
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.impl.pumpEventsForHierarchy
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.io.blockingDispatcher
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import javax.swing.SwingUtilities
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val LOG = Logger.getInstance("#com.intellij.ide.shutdown")

// todo convert ApplicationImpl and IdeEventQueue to kotlin

internal fun cancelAndJoinBlocking(application: ApplicationImpl) {
  EDT.assertIsEdt()
  LOG.assertTrue(!ApplicationManager.getApplication().isWriteAccessAllowed)
  cancelAndJoinBlocking(application.getCoroutineScope(), debugString = "Application $application") { containerJob, dumpJob ->
    containerJob.invokeOnCompletion {
      // Unblock `getNextEvent()` in case it's blocked.
      SwingUtilities.invokeLater(EmptyRunnable.INSTANCE)
    }
    IdeEventQueue.getInstance().pumpEventsForHierarchy {
      containerJob.isCompleted
      // This means container job is still not completed,
      // delayUntilCoroutineDump has passed and dump was already logged
      // => nothing we can do here, just exit the application and don't freeze forever.
      //
      // After returning from blocking, the app is disposed.
      // Returning here means some coroutines leak beyond the scope, i.e. they may continue running.
      // Running coroutines may result is various exceptions,
      // e.g. NPEs once `ApplicationManager.setApplication(null)` is completed.
      // The coroutine dump is logged and should be investigated before considering exceptions from leaked coroutines.
      || dumpJob.isCompleted
    }
  }
}

internal fun cancelAndJoinBlocking(project: ProjectImpl) {
  cancelAndJoinBlocking(project.getCoroutineScope(), debugString = "Project $project") { job, _ ->
    runWithModalProgressBlocking(ModalTaskOwner.guess(), IdeBundle.message("progress.closing.project"), TaskCancellation.nonCancellable()) {
      job.join()
    }
  }
}

internal fun cancelAndJoinBlocking(
  containerScope: CoroutineScope,
  debugString: String,
  joinBlocking: (containerJob: Job, dumpJob: Job) -> Unit,
) {
  if (!Registry.`is`("ide.await.scope.completion", true)) {
    return
  }
  val containerJob = containerScope.coroutineContext.job
  LOG.trace("$debugString: joining scope")
  containerJob.cancel()
  if (containerJob.isCompleted) {
    LOG.trace("$debugString: scope is already completed")
    return
  }
  LOG.trace("$debugString: waiting for scope completion")
  @OptIn(DelicateCoroutinesApi::class)
  val dumpJob = GlobalScope.launch(@OptIn(IntellijInternalApi::class) blockingDispatcher) {
    delay(delayUntilCoroutineDump)
    LOG.warn("$debugString: scope was not completed in $delayUntilCoroutineDump.\n${dumpCoroutines(scope = containerScope, stripDump = false)}")
  }
  try {
    joinBlocking(containerJob, dumpJob)
  }
  finally {
    dumpJob.cancel()
  }
  LOG.trace("$debugString: scope was completed")
}

private val delayUntilCoroutineDump: Duration = 10.seconds
