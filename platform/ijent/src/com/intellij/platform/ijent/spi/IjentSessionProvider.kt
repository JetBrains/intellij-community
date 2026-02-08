// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.IjentSessionRegistry

/**
 * Creates an [IjentApi] session from a deployed IJent instance.
 */
interface IjentSessionProvider {
  /**
   * Supposed to be used inside [IjentSessionRegistry.register].
   */
  suspend fun connect(deploymentResult: IjentConnectionContext): IjentSession<*>

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
  override suspend fun connect(deploymentResult: IjentConnectionContext): IjentSession<*> {
    throw IjentStartupError.MissingImplPlugin()
  }
}

/**
 * Creates an [IjentApi] session from deployment result.
 *
 * The session terminates when the IDE exits or when [IjentApi.close] is called.
 */
suspend fun <T : IjentApi> createIjentSession(deploymentResult: IjentConnectionContext): IjentSession<T> {
  @Suppress("UNCHECKED_CAST")
  return IjentSessionProvider.instanceAsync().connect(deploymentResult) as IjentSession<T>
}