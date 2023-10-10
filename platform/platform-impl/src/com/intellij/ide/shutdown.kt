// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.diagnostic.dumpCoroutines
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ModalTaskOwner
import com.intellij.openapi.progress.TaskCancellation
import com.intellij.openapi.progress.impl.pumpEventsForHierarchy
import com.intellij.openapi.progress.runWithModalProgressBlocking
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.io.blockingDispatcher
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import javax.swing.SwingUtilities
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val LOG = Logger.getInstance("#com.intellij.ide.shutdown")

// todo convert ApplicationImpl and IdeEventQueue to kotlin

internal fun cancelAndJoinExistingContainerCoroutines(application: ApplicationImpl) {
  val containerScope = application.scopeHolder.containerScope
  val joiner = containerScope.cancelAndJoinChildren()
  dumpCoroutinesAfterTimeout("Application $application children", containerScope) {
    joiner.invokeOnCompletion {
      // Unblock `getNextEvent()` in case it's blocked.
      SwingUtilities.invokeLater(EmptyRunnable.INSTANCE)
    }
    IdeEventQueue.getInstance().pumpEventsForHierarchy {
      joiner.isCompleted
    }
  }
}

internal fun cancelAndJoinExistingContainerCoroutines(project: ProjectImpl) {
  // Some services (for example, `com.intellij.vcs.log.impl.VcsProjectLog.dropLogManager`)
  // expect `project.messageBus` to work after cancellation of the service scope
  // => service scope must be cancelled and joined before `startDispose()`.
  //
  // Initialization of services happens in a child coroutine of container scope
  // => cancellation of container scope prevents initialization of services.
  // Some listeners (namely `EditorFactoryListener.editorReleased`) request other services during `startDispose()`
  // => we cannot cancel the project scope before `startDispose()`.
  // Requesting an uninitialized service during disposal is incorrect,
  // but it's legacy, and we have to live with it for a while.
  val containerScope = project.scopeHolder.containerScope
  val joiner = containerScope.cancelAndJoinChildren()
  dumpCoroutinesAfterTimeout("Project $project children", containerScope) {
    runWithModalProgressBlocking(ModalTaskOwner.guess(), IdeBundle.message("progress.closing.project"), TaskCancellation.nonCancellable()) {
      joiner.join()
    }
  }
  // At this point owner coroutines of existing services are completed,
  // but the container scope is alive, and new services might still be initialized.
}

private fun CoroutineScope.cancelAndJoinChildren(): Job {
  val children = coroutineContext.job.children.toList() // this is racy
  for (child in children) {
    child.cancel()
  }
  @OptIn(DelicateCoroutinesApi::class, IntellijInternalApi::class)
  return GlobalScope.launch(blockingDispatcher) {
    children.joinAll()
  }
}

internal fun cancelAndJoinBlocking(application: ApplicationImpl) {
  EDT.assertIsEdt()
  LOG.assertTrue(!ApplicationManager.getApplication().isWriteAccessAllowed)
  cancelAndJoinBlocking(application.coroutineScope, debugString = "Application $application") { containerJob, dumpJob ->
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
  cancelAndJoinBlocking(project.coroutineScope, debugString = "Project $project") { job, _ ->
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

private fun dumpCoroutinesAfterTimeout(debugString: String, scope: CoroutineScope, action: () -> Unit) {
  @OptIn(DelicateCoroutinesApi::class)
  val dumpJob = GlobalScope.launch(@OptIn(IntellijInternalApi::class) blockingDispatcher) {
    delay(delayUntilCoroutineDump)
    LOG.warn("$debugString: scope was not completed in $delayUntilCoroutineDump.\n${dumpCoroutines(scope = scope, stripDump = false)}")
  }
  try {
    action()
  }
  finally {
    dumpJob.cancel()
  }
}
