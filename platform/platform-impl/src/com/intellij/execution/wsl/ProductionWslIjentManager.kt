// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.util.coroutines.namedChildScope
import com.intellij.util.SuspendingLazy
import com.intellij.util.suspendingLazy
import com.jetbrains.rd.util.concurrentMapOf
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@ApiStatus.Internal
@VisibleForTesting
class ProductionWslIjentManager(private val scope: CoroutineScope) : WslIjentManager {
  private val myCache: MutableMap<String, SuspendingLazy<IjentApi>> = concurrentMapOf()

  override val isIjentAvailable: Boolean
    get() {
      val id = PluginId.getId("intellij.platform.ijent.impl")
      return Registry.`is`("wsl.use.remote.agent.for.launch.processes", true) && PluginManagerCore.getPlugin(id)?.isEnabled == true
    }

  @DelicateCoroutinesApi
  override val processAdapterScope: CoroutineScope = scope

  override suspend fun getIjentApi(wslDistribution: WSLDistribution, project: Project?, rootUser: Boolean): IjentApi {
    return myCache.compute(wslDistribution.id + if (rootUser) ":root" else "") { _, oldHolder ->
      val validOldHolder = when (oldHolder?.isInitialized()) {
        true ->
          if (oldHolder.getInitialized().isRunning) oldHolder
          else null
        false -> oldHolder
        null -> null
      }

      validOldHolder ?: scope.suspendingLazy {
        val scopeName = "IJent on WSL $wslDistribution"
        val ijentScope = scope.namedChildScope(scopeName, CoroutineExceptionHandler { _, err ->
          LOG.error("Unexpected error in $scopeName", err)
        })
        deployAndLaunchIjent(ijentScope, project, wslDistribution, wslCommandLineOptionsModifier = { it.setSudo(rootUser) })
      }
    }!!.getValue()
  }

  @VisibleForTesting
  fun dropCache() {
    myCache.values.removeAll { ijent ->
      if (ijent.isInitialized()) {
        ijent.getInitialized().close()
      }
      true
    }
  }

  companion object {
    private val LOG = logger<WslIjentManager>()
  }
}