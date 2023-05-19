// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.diagnostic.dumpCoroutines
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ModalTaskOwner
import com.intellij.openapi.progress.TaskCancellation
import com.intellij.openapi.progress.impl.pumpEventsUntilJobIsCompleted
import com.intellij.openapi.progress.runBlockingModal
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val LOG = Logger.getInstance("#com.intellij.ide.shutdown")

// todo convert ApplicationImpl and IdeEventQueue to kotlin

internal fun cancelAndJoinBlocking(application: ApplicationImpl) {
  EDT.assertIsEdt()
  LOG.assertTrue(!ApplicationManager.getApplication().isWriteAccessAllowed)
  cancelAndJoinBlocking(application.coroutineScope, debugString = "Application $application") { containerJob ->
    IdeEventQueue.getInstance().pumpEventsUntilJobIsCompleted(containerJob)
  }
}

internal fun cancelAndJoinBlocking(project: ProjectImpl) {
  cancelAndJoinBlocking(project.coroutineScope, debugString = "Project $project") { job ->
    runBlockingModal(ModalTaskOwner.guess(), IdeBundle.message("progress.closing.project"), TaskCancellation.nonCancellable()) {
      job.join()
    }
  }
}

internal fun cancelAndJoinBlocking(containerScope: CoroutineScope, debugString: String, pumpEvents: (Job) -> Unit) {
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
  val dumpJob = GlobalScope.launch {
    delay(delayUntilCoroutineDump)
    LOG.warn("$debugString: scope was not completed in $delayUntilCoroutineDump.\n${dumpCoroutines(scope = containerScope)}")
  }
  try {
    pumpEvents(containerJob)
  }
  finally {
    dumpJob.cancel()
  }
  LOG.trace("$debugString: scope was completed")
}

private val delayUntilCoroutineDump: Duration = 10.seconds
