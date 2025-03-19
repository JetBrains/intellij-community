// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.execution.wsl

import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.platform.ijent.deploy
import com.intellij.platform.ijent.spi.DeployedIjent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * An entry point for running [IjentApi] over WSL and checking if [IjentApi] even should be used for WSL.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
interface WslIjentManager {
  /**
   * This coroutine scope is for usage in [com.intellij.execution.ijent.IjentChildProcessAdapter] and things like that.
   */
  @DelicateCoroutinesApi
  val processAdapterScope: CoroutineScope

  /**
   * The returned instance is not supposed to be closed by the caller. [WslIjentManager] closes [IjentApi] by itself during shutdown.
   */
  suspend fun getIjentApi(wslDistribution: WSLDistribution, project: Project?, rootUser: Boolean): IjentPosixApi

  @Deprecated(
    "Use WslIjentAvailabilityService.runWslCommandsViaIjent",
    replaceWith = ReplaceWith(
      "WslIjentAvailabilityService.getInstance().runWslCommandsViaIjent()",
      "com.intellij.execution.wsl.WslIjentAvailabilityService",
    )
  )
  @get:ApiStatus.Internal
  val isIjentAvailable: Boolean

  companion object {
    @JvmStatic
    fun getInstance(): WslIjentManager = service()

    suspend fun instanceAsync(): WslIjentManager = serviceAsync()
  }
}

internal suspend fun deployAndLaunchIjent(
  parentScope: CoroutineScope,
  project: Project?,
  ijentLabel: String,
  wslDistribution: WSLDistribution,
  wslCommandLineOptionsModifier: (WSLCommandLineOptions) -> Unit = {},
): IjentPosixApi =
  deployAndLaunchIjentGettingPath(parentScope, project, ijentLabel, wslDistribution, wslCommandLineOptionsModifier).ijentApi

@VisibleForTesting
suspend fun deployAndLaunchIjentGettingPath(
  parentScope: CoroutineScope,
  project: Project?,
  ijentLabel: String,
  wslDistribution: WSLDistribution,
  wslCommandLineOptionsModifier: (WSLCommandLineOptions) -> Unit = {},
): DeployedIjent.Posix {
  return WslIjentDeployingStrategy(
    scope = parentScope,
    ijentLabel = ijentLabel,
    distribution = wslDistribution,
    project = project,
    wslCommandLineOptionsModifier = wslCommandLineOptionsModifier
  ).deploy()
}