// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.DumbModeWhileScanningTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class DumbModeFromScanningTrackerService(private val project: Project, cs: CoroutineScope) {
  private var dumbModeStartCallback: AtomicReference<Runnable?> = AtomicReference()

  init {
    cs.launch {
      project.service<DumbModeWhileScanningTrigger>().isDumbModeForScanningActive().collect { value ->
        if (value) {
          dumbModeStartCallback.get()?.run()
        }
      }
    }
  }

  fun setScanningDumbModeStartCallback(callback: Runnable) {
    if (ApplicationManagerEx.isInIntegrationTest()) {
      thisLogger().info("setScanningDumbModeStartCallback: $callback")
    }

    val oldValue = dumbModeStartCallback.getAndSet(callback)
    if (oldValue != null) {
      thisLogger().error("Callback already provided: $oldValue")
    }
  }

  fun cleanScanningDumbModeStartCallback(callback: Runnable) {
    if (ApplicationManagerEx.isInIntegrationTest()) {
      thisLogger().info("cleanScanningDumbModeStartCallback: $callback")
    }

    val oldValue = dumbModeStartCallback.getAndSet(null)
    if (oldValue != callback) {
      thisLogger().error("Callback already removed: $oldValue")
    }
  }
}
