// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.tcp

import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.spi.IjentConnectionStrategy
import com.intellij.platform.ijent.spi.IjentDeployingStrategy
import com.intellij.platform.ijent.spi.IjentSessionMediator
import com.intellij.platform.ijent.spi.createIjentSession

/**
 * Strategy for deploying and launching IJent in an environment where the copying and launching arbitrary executables is not allowed.
 * That means that the delivery and launch of IJent is deployed to the external application
 */
abstract class IjentIsolatedTcpDeployingStrategy : IjentDeployingStrategy {
  /**
   * Deploys and lauch IJent in the target environment.
   * @return [IjentSessionMediator] with the scope of running IJent
   */
  protected abstract suspend fun deployEnvironment(): IjentSessionMediator
  abstract override suspend fun getConnectionStrategy(): IjentConnectionStrategy.Tcp
  abstract suspend fun getRemoteBinaryPath(): String

  protected abstract fun closeConnection()

  override suspend fun <T : IjentApi> createIjentSession(): IjentSession<T> =
    try {
      val mediator = deployEnvironment()
      createIjentSession(getConnectionStrategy(),
                         getRemoteBinaryPath(),
                         getTargetPlatform(),
                         mediator)
    } catch (err: Exception) {
      closeConnection()
      throw err
    }
}