// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentSession
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.annotations.ApiStatus

/**
 * Strategy for deploying and launching ijent on target environment which is used to create an IjentApi instance.
 *
 * Use [createIjentSession] for launching IJent using this interface.
 *
 * Every instance of [IjentDeployingStrategy] is used to start exactly one IJent.
 *
 * @see IjentApi
 * @see IjentControlledEnvironmentDeployingStrategy
 */
@ApiStatus.OverrideOnly
interface IjentDeployingStrategy {
  @ApiStatus.Internal
  enum class DeployEvent { DEPLOY_STARTED, DEPLOY_FINISHED, CONNECT_STARTED, CONNECT_FINISHED }

  companion object {
    @ApiStatus.Internal
    val deployEvents: MutableSharedFlow<DeployEvent> = MutableSharedFlow()
  }

  /**
   * Creates a new IJent session in the target environment.
   * This method should be called exactly once.
   */
  suspend fun createIjentSession(provider: IjentSessionProvider): IjentSession
}