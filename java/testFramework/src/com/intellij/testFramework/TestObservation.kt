// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.diagnostic.ThreadDumper
import com.intellij.diagnostic.dumpCoroutines
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.Observation
import com.intellij.testFramework.concurrency.waitForPromiseAndPumpEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.concurrency.asPromise
import java.util.function.Consumer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object TestObservation {

  @JvmStatic
  @JvmOverloads
  fun waitForConfiguration(timeout: Long, project: Project, messageCallback: Consumer<String>? = null) {
    waitForConfiguration(timeout.milliseconds, project, messageCallback?.let { it::accept })
  }

  fun waitForConfiguration(timeout: Duration, project: Project, messageCallback: ((String) -> Unit)? = null) {
    val coroutineScope = CoroutineScopeService.getCoroutineScope(project)
    val job = coroutineScope.launch {
      awaitConfiguration(timeout, project, messageCallback)
    }
    val promise = job.asPromise()
    promise.waitForPromiseAndPumpEdt(Duration.INFINITE)
  }

  suspend fun awaitConfiguration(timeout: Duration, project: Project, messageCallback: ((String) -> Unit)? = null) {
    try {
      withTimeout(timeout) {
        Observation.awaitConfiguration(project, messageCallback)
      }
    }
    catch (_: TimeoutCancellationException) {
      throwWaitingTimoutError(timeout)
    }
  }

  private fun throwWaitingTimoutError(timeout: Duration): Nothing {
    val coroutineDump = dumpCoroutines()
    val threadDump = ThreadDumper.dumpThreadsToString()
    throw AssertionError("""
        |The waiting takes too long. Expected to take no more than $timeout ms.
        |------- Thread dump begin -------
        |$threadDump
        |-------- Thread dump end --------
        |------ Coroutine dump begin -----
        |$coroutineDump
        |------- Coroutine dump end ------
      """.trimMargin())
  }

  @Service(Service.Level.PROJECT)
  private class CoroutineScopeService(private val coroutineScope: CoroutineScope) {
    companion object {
      fun getCoroutineScope(project: Project): CoroutineScope {
        return project.service<CoroutineScopeService>().coroutineScope
      }
    }
  }
}