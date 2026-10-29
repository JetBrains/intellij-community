// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.platform.ijent.IjentProvisioningProgress
import com.intellij.platform.util.annotations.VisibleToClasses
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/** Shows binary provisioning in the caller's indicator and keeps its modality for plugin installation. */
@ApiStatus.Internal
@VisibleToClasses(
  "com.intellij.platform.ijent.ssh.IndicatorIjentProvisioningProgressTest",
  "com.intellij.platform.ijent.ssh.impl.PlatformSshEelApiFactory",
)
class IndicatorIjentProvisioningProgress(
  private val indicator: ProgressIndicator,
  private val callerJob: Job,
) : IjentProvisioningProgress {
  override suspend fun <T> withProgress(text: @Nls String, task: suspend () -> T): T =
    withContext(indicator.modalityState.asContextElement()) {
      coroutineScope {
        val provisioningJob = coroutineContext.job
        val cancellationHandle = callerJob.invokeOnCompletion { cause ->
          if (cause != null) provisioningJob.cancel()
        }
        val previousText = indicator.text
        indicator.text = text
        try {
          indicator.checkCanceled()
          task()
        }
        finally {
          indicator.text = previousText
          cancellationHandle.dispose()
        }
      }
    }
}
