// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.openapi.project.Project
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.util.SuspendingLazy
import com.intellij.util.suspendingLazy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
@VisibleForTesting
class ProductionWslIjentManager(private val scope: CoroutineScope) : WslIjentManager {
  private val myCache: MutableMap<String, SuspendingLazy<IjentPosixApi>> = ConcurrentHashMap()

  override val isIjentAvailable: Boolean
    get() = WslIjentAvailabilityService.getInstance().runWslCommandsViaIjent()

  @DelicateCoroutinesApi
  override val processAdapterScope: CoroutineScope = scope

  override suspend fun getIjentApi(wslDistribution: WSLDistribution, project: Project?, rootUser: Boolean): IjentPosixApi {
    return myCache.compute(wslDistribution.id + if (rootUser) ":root" else "") { _, oldHolder ->
      val validOldHolder = when (oldHolder?.isInitialized()) {
        true -> {
          val ijentApi =
            try {
              oldHolder.getInitialized()
            }
            catch (ignored: Exception) {
              // Something wrong happened. It is supposed that the error is logged by the caller side that called this method the first.
              // There should be an attempt to start IJent again.
              null
            }
          if (ijentApi?.isRunning == true) oldHolder
          else null
        }
        false -> oldHolder
        null -> null
      }

      validOldHolder ?: scope.suspendingLazy(CoroutineName("IJent on WSL $wslDistribution")) {
        deployAndLaunchIjent(project, wslDistribution, wslCommandLineOptionsModifier = { it.setSudo(rootUser) })
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
}