// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentSession
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
  /**
   * Architecture of the ijent binary that will be copied to the target machine.
   * Typically, the ijent architecture should match the target environment architecture.
   *
   * The implementation doesn't need to cache the result, because the function is called exactly once.
   *
   * @see com.intellij.platform.ijent.IjentExecFileProvider.getIjentBinary
   */
  suspend fun getTargetPlatform(): EelPlatform

  /**
   * Defines a set of options for connecting to a running IJent
   * This step is logically different from deployment,
   * and here we allow to configure the actual process of initial message exchange.
   *
   * @see IjentConnectionStrategy
   */
  suspend fun getConnectionStrategy(): IjentConnectionStrategy

  /**
   * Creates a new IJent session in the target environment.
   * This method should be called exactly once.
   */
  suspend fun <T : IjentApi> createIjentSession(): IjentSession<T>

  interface Posix : IjentDeployingStrategy {
    /** @see [IjentDeployingStrategy.getTargetPlatform] */
    override suspend fun getTargetPlatform(): EelPlatform.Posix
  }

  interface Windows : IjentDeployingStrategy {
    /** @see [IjentDeployingStrategy.getTargetPlatform] */
    override suspend fun getTargetPlatform(): EelPlatform.Windows
  }
}