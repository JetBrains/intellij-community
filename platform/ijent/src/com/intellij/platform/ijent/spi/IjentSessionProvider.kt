// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.platform.ijent.IjentSessionRegistry
import com.intellij.platform.ijent.IjentWindowsApi

/**
 * Given that there is some IJent process launched, this extension gets handles to stdin+stdout of the process and returns
 * an [com.intellij.platform.eel.IjentApi] instance for calling procedures on IJent side.
 */
interface IjentSessionProvider {
  /**
   * Supposed to be used inside [IjentSessionRegistry.register].
   */
  suspend fun connect(
    strategy: IjentConnectionStrategy,
    platform: EelPlatform,
    descriptor: EelDescriptor,
    mediator: IjentSessionMediator,
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
  override suspend fun connect(strategy: IjentConnectionStrategy, platform: EelPlatform, descriptor: EelDescriptor, mediator: IjentSessionMediator): IjentApi {
    throw IjentStartupError.MissingImplPlugin()
  }
}

/**
 * Make [IjentApi] from an already running [process].
 * [ijentName] is used for debugging utilities like logs and thread names.
 *
 * The process terminates automatically only when the IDE exits, or if [IjentApi.close] is called explicitly.
 */
suspend fun connectToRunningIjent(strategy: IjentConnectionStrategy, platform: EelPlatform, descriptor: EelDescriptor, mediator: IjentSessionMediator): IjentApi {
  mediator.myExitPolicy = IjentSessionMediator.ProcessExitPolicy.CHECK_CODE
  return IjentSessionProvider.instanceAsync().connect(strategy, platform, descriptor, mediator)
}

/** A specialized overload of [connectToRunningIjent] */
suspend fun connectToRunningIjent(
  strategy: IjentConnectionStrategy,
  platform: EelPlatform.Posix,
  descriptor: EelDescriptor,
  mediator: IjentSessionMediator,
): IjentPosixApi =
  connectToRunningIjent(strategy, platform as EelPlatform, descriptor, mediator) as IjentPosixApi

/** A specialized overload of [connectToRunningIjent] */
suspend fun connectToRunningIjent(
  strategy: IjentConnectionStrategy,
  platform: EelPlatform.Windows,
  descriptor: EelDescriptor,
  mediator: IjentSessionMediator,
): IjentWindowsApi =
  connectToRunningIjent(strategy, platform as EelPlatform, descriptor, mediator) as IjentWindowsApi
