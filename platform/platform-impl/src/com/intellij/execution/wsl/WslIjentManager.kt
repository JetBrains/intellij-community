// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.platform.ijent.IjentSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import org.jetbrains.annotations.ApiStatus

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
   * Acquires an [IjentPosixApi] for the given WSL distribution.
   *
   * ## Descriptor Resolution
   * If [descriptor] is `null`, a fallback resolution is performed:
   * - If [project] is provided, [getEelDescriptor] is called on the project to derive the [EelDescriptor].
   * - If [project] is also `null`, a default descriptor is created from [wslDistribution] using [WslEelDescriptor].
   *
   * This ensures that an [EelDescriptor] is always passed to [IjentSession.getIjentInstance], which may influence
   * environment configuration or file path resolution.
   *
   * ## Lifecycle
   * - The returned [IjentPosixApi] is managed internally and **must not** be closed by the caller.
   * - The underlying session is closed automatically when the enclosing coroutine scope completes.
   *
   * @param descriptor An optional descriptor pointing to a specific environment path.
   * @param wslDistribution The target WSL distribution (e.g. Ubuntu, Debian).
   * @param project An optional project, used as a fallback source for descriptor resolution.
   * @param rootUser Whether the Ijent should run with root privileges (`sudo`).
   * @return An initialized [IjentPosixApi] ready for interaction with the WSL environment.
   */
  suspend fun getIjentApi(descriptor: EelDescriptor?, wslDistribution: WSLDistribution, project: Project?, rootUser: Boolean): IjentPosixApi

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