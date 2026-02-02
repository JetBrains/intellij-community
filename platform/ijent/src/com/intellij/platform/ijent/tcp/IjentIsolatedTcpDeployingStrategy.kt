// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.tcp

import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.spi.IjentDeployingStrategy
import com.intellij.platform.ijent.spi.IjentConnectionContext
import com.intellij.platform.ijent.spi.createIjentSession

/**
 * Strategy for deploying IJent over TCP when standard process spawning is not available
 * (e.g., SSH connections where IJent must be uploaded and executed remotely).
 *
 * Implementations handle binary deployment, process lifecycle, and port forwarding.
 */
abstract class IjentIsolatedTcpDeployingStrategy : IjentDeployingStrategy {
  /**
   * Deploys and launches IJent in the target environment.
   * @return [IjentConnectionContext] with the mediator and deployment metadata
   */
  protected abstract suspend fun deploy(): IjentConnectionContext

  final override suspend fun <T : IjentApi> createIjentSession(): IjentSession<T> = createIjentSession(deploy())
}