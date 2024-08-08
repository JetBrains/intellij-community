// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.platform.ijent.IjentId
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.platform.ijent.IjentSessionRegistry
import com.intellij.platform.ijent.bindToScope
import com.intellij.platform.ijent.spi.IjentThreadPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
@VisibleForTesting
class ProductionWslIjentManager(private val scope: CoroutineScope) : WslIjentManager {
  private val myCache: MutableMap<String, IjentId> = ConcurrentHashMap()

  override val isIjentAvailable: Boolean
    get() = WslIjentAvailabilityService.getInstance().runWslCommandsViaIjent()

  @DelicateCoroutinesApi
  override val processAdapterScope: CoroutineScope = run {
    // It's certainly known that `scope` has a parent job inside, so calling `CoroutineScope()` is safe here.
    @Suppress("RAW_SCOPE_CREATION")
    CoroutineScope(scope.coroutineContext + IjentThreadPool.asCoroutineDispatcher())
  }

  override suspend fun getIjentApi(wslDistribution: WSLDistribution, project: Project?, rootUser: Boolean): IjentPosixApi {
    val ijentSessionRegistry = IjentSessionRegistry.instanceAsync()
    val ijentId = myCache.computeIfAbsent("""wsl:${wslDistribution.id}${if (rootUser) ":root" else ""}""") { ijentName ->
      val ijentId = ijentSessionRegistry.register(ijentName, oneOff = false) {
        val ijent = deployAndLaunchIjent(project, wslDistribution, wslCommandLineOptionsModifier = { it.setSudo(rootUser) })
        ijent.bindToScope(scope)
        ijent
      }
      scope.coroutineContext.job.invokeOnCompletion {
        ijentSessionRegistry.unregister(ijentId)
        myCache.remove(ijentName)
      }
      ijentId
    }
    return ijentSessionRegistry.get(ijentId) as IjentPosixApi
  }

  @VisibleForTesting
  fun dropCache() {
    val ijentSessionRegistry = serviceIfCreated<IjentSessionRegistry>()
    myCache.values.removeAll { ijentId ->
      ijentSessionRegistry?.unregister(ijentId)
      true
    }
  }
}