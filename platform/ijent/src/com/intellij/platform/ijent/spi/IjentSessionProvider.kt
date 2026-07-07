// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentSession

/**
 * Creates an [IjentApi] session from a deployed IJent instance.
 */
interface IjentSessionProvider {
  /**
   * Supposed to be used when registering a new IJent session.
   */
  suspend fun connect(deploymentResult: IjentConnectionContext): IjentSession
}

sealed class IjentStartupError : RuntimeException {
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable) : super(message, cause)

  sealed class BootstrapOverShell : IjentStartupError {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
  }

  class IncompatibleTarget(message: String) : BootstrapOverShell(message)
  class CommunicationError(cause: Throwable) : BootstrapOverShell(cause.message.orEmpty(), cause)
}