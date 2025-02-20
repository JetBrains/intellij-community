// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)

package com.intellij.ide

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.diagnostic.PlatformMemoryUtil
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.isRhizomeProgressEnabled
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.UnindexedFilesScannerExecutor
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.platform.ide.progress.activeTasks
import com.intellij.platform.ide.progress.updates
import com.intellij.util.io.DirectByteBufferAllocator
import com.intellij.util.io.StorageLockContext
import com.intellij.util.io.storage.HeavyProcessLatch
import fleet.kernel.rete.asValuesFlow
import fleet.kernel.rete.tokensFlow
import fleet.kernel.tryWithEntities
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.APP)
class AppIdleMemoryCleaner(private val cs: CoroutineScope) {
  private val isDeactivated: MutableStateFlow<Boolean> = MutableStateFlow(true)
  private val hasActiveBackgroundTasks: StateFlow<Boolean> = cs.hasActiveBackgroundTasksStateFlow()

  private var job: Job? = null

  private var lastCleaningTime: Long = 0L

  init {
    isDeactivated.onEach {
      if (it) {
        job = cs.launch { allWindowsDeactivated() }
      } else {
        job?.cancel()
        job = null
      }
    }.launchIn(cs)
  }

  private class MyApplicationActivationListener : ApplicationActivationListener {
    override fun applicationActivated(ideFrame: IdeFrame) {
      serviceIfCreated<AppIdleMemoryCleaner>()?.apply {
        isDeactivated.value = false
      }
    }

    override fun applicationDeactivated(ideFrame: IdeFrame) {
      if (!Registry.`is`("ide.idle.memory.cleaner.enabled")) return
      if (isModal()) {
        // Modal state hints at some ongoing activity
        return
      }
      service<AppIdleMemoryCleaner>().apply {
        isDeactivated.value = true
      }
    }
  }

  /**
   * The function starts executing when all application windows are deactivated (unfocused)
   * and is canceled when any application window is activated
   */
  @Suppress("SimplifyBooleanWithConstants")
  private suspend fun allWindowsDeactivated() {
    delay(Registry.get("ide.idle.memory.cleaner.delay").asInteger().toLong())
    for (project in ProjectManager.getInstance().openProjects) {
      val scanner = project.serviceIfCreated<UnindexedFilesScannerExecutor>() ?: continue
      scanner.isRunning.first { it == false }
    }
    hasActiveBackgroundTasks.first { it == false }
    while (hasOngoingHighlightingInAnyProject() || HeavyProcessLatch.INSTANCE.isRunning) {
      // We could use listeners instead, but it looks overkill in this case - we're not in a hurry
      delay(1000)
    }

    val now = System.nanoTime()
    if (now - lastCleaningTime < 60_000_000_000) return
    lastCleaningTime = now

    performCleanup()
  }

  private fun performCleanup() {
    LOG.debug("Performing memory cleanup")
    runGc()
    releaseIndexCachedDirectBuffers()
    PlatformMemoryUtil.getInstance().trimLinuxNativeHeap()
  }

  private fun releaseIndexCachedDirectBuffers() {
    // This cache is allocated off-heap and can be quickly retuned to the OS.
    // Also, this cache is short-living: it takes little time to allocate and fill it back.
    StorageLockContext.forceDirectMemoryCache()
    DirectByteBufferAllocator.ALLOCATOR.releaseCachedBuffers()
  }

  private fun runGc() {
    // `System.gc()` makes the heap to shrink respecting `-XX:MaxHeapFreeRatio` VM option, so some memory is returned to OS.
    // At the same time, it leads to a long GC pause (hundreds of milliseconds). This should be OK since the IDE is idle.
    System.gc()
  }
}

private val LOG: Logger = logger<AppIdleMemoryCleaner>()

private suspend fun hasOngoingHighlightingInAnyProject(): Boolean {
  return readAction {
    ProjectManager.getInstance().openProjects.any { project ->
      DaemonCodeAnalyzer.getInstance(project).isRunning
    }
  }
}

private fun isModal(): Boolean {
  return LaterInvocator.isInModalContext() || ProgressManager.getInstance().hasModalProgressIndicator()
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun CoroutineScope.hasActiveBackgroundTasksStateFlow(): StateFlow<Boolean> {
  if (!isRhizomeProgressEnabled) {
    return MutableStateFlow(false)
  }
  return activeTasks.asValuesFlow().flatMapMerge { task ->
    flow {
      emit(1)
      tryWithEntities(task) { task.updates.tokensFlow().collect {} }
      emit(-1)
    }
  }
    .scan(0) { acc, delta -> acc + delta }
    .map { count -> count > 0 }
    .debounce { if (it) 0 else 2000 }
    .stateIn(this, SharingStarted.Eagerly, false)
}
