// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentId
import com.intellij.platform.ijent.IjentPlatform
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.platform.ijent.IjentSessionRegistry
import com.intellij.platform.ijent.IjentWindowsApi

/**
 * Given that there is some IJent process launched, this extension gets handles to stdin+stdout of the process and returns
 * an [IjentApi] instance for calling procedures on IJent side.
 */
interface IjentSessionProvider {
  /**
   * Supposed to be used inside [IjentSessionRegistry.register].
   */
  suspend fun connect(
    ijentId: IjentId,
    platform: IjentPlatform,
    mediator: IjentSessionMediator
  ): IjentApi

  companion object {
    suspend fun instanceAsync(): IjentSessionProvider = serviceAsync()
  }
}

sealed class IjentStartupError : RuntimeException {
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable) : super(message, cause)

  class MissingImplPlugin : IjentStartupError("The plugin `intellij.platform.ijent.impl` is not installed")

  sealed class BootstrapOverShell : IjentStartupError {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
  }

  class IncompatibleTarget(message: String) : BootstrapOverShell(message)
  class CommunicationError(cause: Throwable) : BootstrapOverShell(cause.message.orEmpty(), cause)
}

internal class DefaultIjentSessionProvider : IjentSessionProvider {
  override suspend fun connect(ijentId: IjentId, platform: IjentPlatform, mediator: IjentSessionMediator): IjentApi {
    throw IjentStartupError.MissingImplPlugin()
  }
}

/**
 * Make [IjentApi] from an already running [process].
 * [ijentName] is used for debugging utilities like logs and thread names.
 *
 * The process terminates automatically only when the IDE exits, or if [IjentApi.close] is called explicitly.
 * [com.intellij.platform.ijent.bindToScope] may be useful for terminating the IJent process earlier.
 */
suspend fun connectToRunningIjent(ijentName: String, platform: IjentPlatform, process: Process): IjentApi =
  IjentSessionRegistry.instanceAsync().register(ijentName) { ijentId ->
    val mediator = IjentSessionMediator.create(process, ijentId)
    mediator.expectedErrorCode = IjentSessionMediator.ExpectedErrorCode.ZERO
    IjentSessionProvider.instanceAsync().connect(ijentId, platform, mediator)
  }

/** A specialized overload of [connectToRunningIjent] */
suspend fun connectToRunningIjent(ijentName: String, platform: IjentPlatform.Posix, process: Process): IjentPosixApi =
  connectToRunningIjent(ijentName, platform as IjentPlatform, process) as IjentPosixApi

/** A specialized overload of [connectToRunningIjent] */
suspend fun connectToRunningIjent(ijentName: String, platform: IjentPlatform.Windows, process: Process): IjentWindowsApi =
  connectToRunningIjent(ijentName, platform as IjentPlatform, process) as IjentWindowsApi
