// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslIjentAvailabilityService
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.platform.ijent.IjentSession
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@ApiStatus.Internal
@VisibleForTesting
class ProductionWslIjentManager(private val scope: CoroutineScope) : WslIjentManager {
  private val counter = AtomicLong()

  // keyed by ijentIdLabel, e.g. "wsl:Ubuntu:root"
  private val ijents: MutableMap<String, Deferred<IjentSession.Posix>> = ConcurrentHashMap()
  private val initializedIjents: MutableSet<String> = ContainerUtil.newConcurrentSet()

  private fun unregister(label: String): Boolean {
    val deferred = ijents.remove(label)
    if (deferred != null) {
      deferred.invokeOnCompletion { if (it == null) deferred.getCompleted().close() }
      val message = "Explicitly unregistered and closed during initialization: $label"
      deferred.cancel(message, IjentUnavailableException.ClosedByApplication(message))
    }
    return deferred != null
  }

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

  @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
  private suspend fun getIjentSession(
    wslDistribution: WSLDistribution,
    project: Project?,
    rootUser: Boolean,
    sessionScope: ParentOfIjentScopes,
  ): IjentSession.Posix {
    val label = ijentIdLabel(wslDistribution, rootUser)
    val currentDispatcher = currentCoroutineDispatcher()

    val deferred = ijents.compute(label) { key, oldDeferred ->
      val reused: Deferred<IjentSession.Posix>? = when {
        oldDeferred == null -> null
        !oldDeferred.isCompleted -> oldDeferred
        oldDeferred.getCompletionExceptionOrNull() != null -> null
        oldDeferred.getCompleted().isRunning -> oldDeferred
        else -> null
      }

      reused ?: run {
        val sessionLabel = "ijent-${counter.getAndIncrement()}-${key.replace(Regex("[^A-Za-z0-9-]"), "-")}"
        val actual = GlobalScope.async(currentDispatcher, start = CoroutineStart.LAZY) {
          createIjentSession(wslDistribution, project, rootUser, sessionScope, sessionLabel)
        }

        sessionScope.s.coroutineContext.job.invokeOnCompletion {
          unregister(key)
        }

        actual
      }
    }!!

    initializedIjents.add(label)

    try {
      return deferred.await()
    }
    catch (err: Throwable) {
      throw IjentUnavailableException.unwrapFromCancellationExceptions(err)
    }
  }

  private suspend fun createIjentSession(
    wslDistribution: WSLDistribution,
    project: Project?,
    rootUser: Boolean,
    sessionScope: ParentOfIjentScopes,
    sessionLabel: String,
  ): IjentSession.Posix {
    val session = wslDistribution.createIjentSession(
      sessionScope,
      project,
      sessionLabel,
      wslCommandLineOptionsModifier = { it.setSudo(rootUser) },
    )
    sessionScope.s.coroutineContext.job.invokeOnCompletion {
      session.close()
    }
    return session
  }

  override suspend fun getIjentApi(descriptor: EelDescriptor?, wslDistribution: WSLDistribution, project: Project?, rootUser: Boolean): IjentPosixApi {
    val descriptor = (descriptor ?: (project?.getEelDescriptor() as? WslEelDescriptor) ?: WslEelDescriptor(wslDistribution)) as WslEelDescriptor
    return getIjentSession(wslDistribution, project, rootUser, ParentOfIjentScopes(scope)).getIjentInstance(descriptor)
  }

  override fun isIjentInitialized(descriptor: EelDescriptor): Boolean {
    require(descriptor is WslEelDescriptor)
    return ijentIdLabel(descriptor.distribution, false) in initializedIjents
  }

  private fun ijentIdLabel(wslDistribution: WSLDistribution, rootUser: Boolean): String =
    """wsl:${wslDistribution.id}${if (rootUser) ":root" else ""}"""

  init {
    scope.coroutineContext.job.invokeOnCompletion {
      Cancellation.executeInNonCancelableSection {
        dropCache()
      }
    }
  }

  @VisibleForTesting
  fun dropCache() {
    for (label in ijents.keys.toList()) {
      unregister(label)
    }
  }
}