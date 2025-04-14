// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.diagnostic.dumpCoroutines
import com.intellij.diagnostic.isCoroutineDumpEnabled
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.impl.inModalContext
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.progress.impl.pumpEventsForHierarchy
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.ObjectUtils
import com.intellij.util.io.blockingDispatcher
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import javax.swing.SwingUtilities
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
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
    dumpJob.invokeOnCompletion {
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

internal fun cancelAndTryJoin(project: ProjectImpl) {
  val containerScope = project.getCoroutineScope()
  val debugString = "Project $project"
  LOG.trace { "$debugString: trying to join scope" }
  val containerJob = containerScope.coroutineContext.job
  val start = System.nanoTime()

  containerJob.cancel()
  if (containerJob.isCompleted) {
    LOG.trace { "$debugString: already completed" }
    return
  }

  inModalContext(ObjectUtils.sentinel("$debugString shutdown")) { // enter modality to avoid running arbitrary write actions which
    LOG.trace { "$debugString: flushing EDT queue" }
    IdeEventQueue.getInstance().flushQueue() // flush once to give EDT coroutines a chance to complete
  }
  if (containerJob.isCompleted) {
    val elapsed = System.nanoTime() - start
    // this might mean that the flush helped coroutines to complete OR completion happened on BG during the flush
    LOG.trace { "$debugString: completed after flush in ${elapsed.nanoseconds}" }
    return
  }

  if (!isCoroutineDumpEnabled()) {
    return
  }
  // TODO install and use currentThreadCoroutineScope instead OR make this function suspending
  val applicationScope = (ApplicationManager.getApplication() as ComponentManagerEx).getCoroutineScope()
  applicationScope.launch(@OptIn(IntellijInternalApi::class, DelicateCoroutinesApi::class) blockingDispatcher) {
    val dumpJob = launch {
      delay(delayUntilCoroutineDump)
      LOG.error(
        "$debugString: scope was not completed in $delayUntilCoroutineDump",
        Attachment("coroutineDump.txt", dumpCoroutines(scope = containerScope)!!),
      )
    }
    try {
      containerJob.join()
      val elapsed = System.nanoTime() - start
      LOG.trace { "$debugString: completed in ${elapsed.nanoseconds}" }
      dumpJob.cancel()
    }
    catch (ce: CancellationException) {
      LOG.trace { "$debugString: coroutine dump was cancelled" }
      throw ce
    }
  }
}
