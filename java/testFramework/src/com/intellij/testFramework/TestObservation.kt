// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.diagnostic.ThreadDumper.dumpThreadsToString
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
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object TestObservation {

  @JvmStatic
  fun waitForConfiguration(timeout: Long, project: Project) {
    waitForConfiguration(timeout.milliseconds, project)
  }

  fun waitForConfiguration(timeout: Duration, project: Project) {
    val coroutineScope = CoroutineScopeService.getCoroutineScope(project)
    val job = coroutineScope.launch {
      awaitConfiguration(timeout, project)
    }
    val promise = job.asPromise()
    promise.waitForPromiseAndPumpEdt(Duration.INFINITE)
  }

  suspend fun awaitConfiguration(timeout: Duration, project: Project) {
    val operationLog = StringJoiner("\n")
    try {
      withTimeout(timeout) {
        Observation.awaitConfiguration(project, operationLog::add)
      }
    }
    catch (_: TimeoutCancellationException) {
      val activityDump = Observation.dumpAwaitedActivitiesToString()
      val coroutineDump = dumpCoroutines()
      val threadDump = dumpThreadsToString()

      System.err.println("""
        |The waiting takes too long. Expected to take no more than $timeout ms.
        |------ Operation log begin ------
        |$operationLog
        |------- Operation log end -------
        |------ Activity dump begin ------
        |$activityDump
        |------- Activity dump end -------
        |------- Thread dump begin -------
        |$threadDump
        |-------- Thread dump end --------
        |------ Coroutine dump begin -----
        |$coroutineDump
        |------- Coroutine dump end ------
      """.trimMargin())
      throw AssertionError("The waiting takes too long. Expected to take no more than $timeout ms.")
    }
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