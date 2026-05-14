// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslIjentAvailabilityService
import com.intellij.execution.wsl.WslIjentManager
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ijent.IjentId
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.IjentSessionRegistry
import com.intellij.platform.ijent.ParentOfIjentScopes
import com.intellij.platform.ijent.spi.IjentThreadPool
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
@VisibleForTesting
class ProductionWslIjentManager(private val scope: CoroutineScope) : WslIjentManager {
  private val myCache: MutableMap<String, IjentId> = ConcurrentHashMap()
  private val initializedIjents: MutableSet<String> = ContainerUtil.newConcurrentSet()

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

  private suspend fun getIjentSession(
    wslDistribution: WSLDistribution,
    project: Project?,
    rootUser: Boolean,
    sessionScope: ParentOfIjentScopes,
  ): IjentSession.Posix {
    val ijentIdLabel = ijentIdLabel(wslDistribution, rootUser)
    val ijentId = myCache.computeIfAbsent(ijentIdLabel) { ijentName ->
      val ijentId = IjentSessionRegistry.register(ijentName) { ijentId ->
        val ijentSession = wslDistribution.createIjentSession(
          sessionScope,
          project,
          ijentId.toString(),
          wslCommandLineOptionsModifier = { it.setSudo(rootUser) },
        )
        sessionScope.s.coroutineContext.job.invokeOnCompletion {
          ijentSession.close()
        }
        ijentSession
      }
      sessionScope.s.coroutineContext.job.invokeOnCompletion {
        IjentSessionRegistry.unregister(ijentId)
        myCache.remove(ijentName)
      }
      ijentId
    }
    initializedIjents.add(ijentIdLabel)
    return IjentSessionRegistry.get(ijentId)
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
    myCache.values.removeAll { ijentId ->
      IjentSessionRegistry.unregister(ijentId)
      true
    }
  }
}