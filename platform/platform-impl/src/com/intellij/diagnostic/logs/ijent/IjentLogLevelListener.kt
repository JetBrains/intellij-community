// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.logs.ijent

import com.intellij.diagnostic.logs.LogLevelConfigurationManager
import com.intellij.platform.ijent.RunningGrpcIjentSessionsRegistryService
import com.intellij.util.containers.forEachGuaranteed
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

@VisibleForTesting
@ApiStatus.Internal
class IjentLogLevelListener : LogLevelConfigurationManager.Listener {
  private val updatingCoroutines = Collections.newSetFromMap(ConcurrentHashMap<Job, Boolean>())

  suspend fun awaitAllJobs() {
    updatingCoroutines.forEach { it.join() }
  }

  override fun onCategoriesChanged() {
    // TODO This code should not just enable/disable debug logs, but also should decide, what particular loggers to enable/disable.
    RunningGrpcIjentSessionsRegistryService.sessions.entries.forEachGuaranteed { (session, scope) ->
      val job = scope.s.launch {
        session.updateLogLevel()
      }
      job.invokeOnCompletion { updatingCoroutines.remove(job) }
      updatingCoroutines.add(job)
    }
  }
}