// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslIjentAvailabilityService
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.IjentSessionState
import com.intellij.platform.ijent.IjentUnavailableException
import com.intellij.platform.ijent.ParentOfIjentScopes
import com.intellij.platform.ijent.currentCoroutineDispatcher
import com.intellij.platform.ijent.spi.IjentThreadPool
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@ApiStatus.Internal
@VisibleForTesting
class ProductionWslIjentManager(private val scope: CoroutineScope) : WslIjentManager {
  private class IjentSlot {
    val mutableSessionState = MutableStateFlow<IjentSessionState>(IjentSessionState.NotDeployed)
    val sessionState: StateFlow<IjentSessionState> = mutableSessionState.asStateFlow()

    // Guarded by ijentSlots.compute*.
    var deploymentScope: CoroutineScope? = null
    var deferred: Deferred<IjentSession.Posix>? = null
  }

  private val counter = AtomicLong()

  // keyed by ijentIdLabel, e.g. "wsl:Ubuntu:root"
  private val ijentSlots: MutableMap<String, IjentSlot> = ConcurrentHashMap()
  private val initializedIjents: MutableSet<String> = ContainerUtil.newConcurrentSet()

  @Deprecated("Use WslIjentAvailabilityService.runWslCommandsViaIjent",
              replaceWith = ReplaceWith("WslIjentAvailabilityService.getInstance().runWslCommandsViaIjent()",
                                        "com.intellij.execution.wsl.WslIjentAvailabilityService"))
  override val isIjentAvailable: Boolean
    get() = WslIjentAvailabilityService.getInstance().runWslCommandsViaIjent()

  @DelicateCoroutinesApi
  override val processAdapterScope: CoroutineScope = run {
    scope.childScope(
      name = "IjentChildProcessAdapter scope for all WSL",
      context = IjentThreadPool.coroutineContext,
      supervisor = true,
    )
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override suspend fun getIjentSession(
    wslDistribution: WSLDistribution,
    project: Project?,
    rootUser: Boolean,
    sessionScope: ParentOfIjentScopes,
  ): IjentSession.Posix {
    val label = ijentIdLabel(wslDistribution, rootUser)
    val currentDispatcher = currentCoroutineDispatcher()

    var deferred: Deferred<IjentSession.Posix>? = null
    ijentSlots.compute(label) { key, oldSlot ->
      val slot = oldSlot ?: IjentSlot()
      val deploymentScope = slot.deploymentScope ?: sessionScope.s.childScope(
        name = "IJent deployment scope for $key",
        supervisor = true,
      ).also {
        slot.deploymentScope = it
      }
      val oldDeferred = slot.deferred
      val reused: Deferred<IjentSession.Posix>? = when {
        oldDeferred == null -> null
        !oldDeferred.isCompleted -> oldDeferred
        oldDeferred.getCompletionExceptionOrNull() != null -> null
        oldDeferred.getCompleted().isRunning -> oldDeferred
        else -> null
      }

      deferred = reused ?: run {
        val sessionLabel = "ijent-${counter.getAndIncrement()}-${key.replace(Regex("[^A-Za-z0-9-]"), "-")}"
        deploymentScope.async(currentDispatcher, start = CoroutineStart.LAZY) {
          slot.mutableSessionState.value = IjentSessionState.Deploying
          try {
            createIjentSession(wslDistribution, project, rootUser, sessionScope, sessionLabel).also {
              slot.mutableSessionState.value = IjentSessionState.Deployed(it)
            }
          }
          catch (err: Throwable) {
            val cause = IjentUnavailableException.unwrapFromCancellationExceptions(err) ?: err
            slot.mutableSessionState.value = if (!currentCoroutineContext().isActive) {
              IjentSessionState.NotDeployed
            }
            else IjentSessionState.Failed(cause)
            throw cause
          }
        }
      }
      slot.deferred = deferred

      slot
    }

    initializedIjents.add(label)
    return checkNotNull(deferred).await()
  }

  override fun getIjentSessionState(wslDistribution: WSLDistribution, rootUser: Boolean): StateFlow<IjentSessionState> {
    val label = ijentIdLabel(wslDistribution, rootUser)
    return ijentSlots.computeIfAbsent(label) { IjentSlot() }.sessionState
  }

  private suspend fun createIjentSession(
    wslDistribution: WSLDistribution,
    project: Project?,
    rootUser: Boolean,
    sessionScope: ParentOfIjentScopes,
    sessionLabel: String,
  ): IjentSession.Posix {
    return wslDistribution.createIjentSession(
      sessionScope,
      project,
      sessionLabel,
      wslCommandLineOptionsModifier = { it.setSudo(rootUser) },
    )
  }

  override suspend fun getIjentApi(
    descriptor: EelDescriptor?,
    wslDistribution: WSLDistribution,
    project: Project?,
    rootUser: Boolean,
  ): IjentPosixApi {
    val descriptor =
      (descriptor ?: (project?.getEelDescriptor() as? WslEelDescriptor) ?: WslEelDescriptor(wslDistribution)) as WslEelDescriptor
    return getIjentSession(wslDistribution, project, rootUser, ParentOfIjentScopes(scope)).getIjentInstance(descriptor)
  }

  override fun isIjentInitialized(descriptor: EelDescriptor): Boolean {
    require(descriptor is WslEelDescriptor)
    return ijentIdLabel(descriptor.distribution, false) in initializedIjents
  }

  private fun ijentIdLabel(wslDistribution: WSLDistribution, rootUser: Boolean): String =
    """wsl:${wslDistribution.id}${if (rootUser) ":root" else ""}"""

  @TestOnly
  fun dropCache() {
    for (label in ijentSlots.keys.toList()) {
      var deferred: Deferred<IjentSession.Posix>? = null
      ijentSlots.computeIfPresent(label) { _, slot ->
        slot.mutableSessionState.value = IjentSessionState.NotDeployed
        deferred = slot.deferred
        slot.deferred = null
        slot
      }
      val deferredToCancel = deferred ?: continue
      deferredToCancel.invokeOnCompletion {
        @OptIn(ExperimentalCoroutinesApi::class)
        if (it == null) deferredToCancel.getCompleted().close()
      }
      val message = "Explicitly unregistered and closed during initialization: $label"
      deferredToCancel.cancel(message, IjentUnavailableException.ClosedByApplication(message, null))
    }
  }
}