// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.eel.EelPlatform
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Strategy for deploying and launching ijent on target environment which is used to create an IjentApi instance.
 *
 * Use [deploy] for launching IJent using this interface.
 *
 * Every instance of [IjentDeployingStrategy] is used to start exactly one IJent.
 *
 * @see com.intellij.platform.eel.IjentApi
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
   * Should start the ijent process.
   *
   * This function is called the last and called exactly once.
   *
   * After it has been called, only [close] may be called.
   * Nothing else will be called for the same instance of [IjentDeployingStrategy].
   *
   * @see com.intellij.platform.ijent.getIjentGrpcArgv
   * @param binaryPath path to ijent binary on target environment
   * @return process that will be used for communication
   */
  suspend fun createProcess(binaryPath: String): IjentSessionMediator

  /**
   * Copy files to the target environment. Typically used to transfer the ijent binary to the target machine.
   *
   * @param file path to local file that should be copied to target environment.
   */
  suspend fun copyFile(file: Path): String

  /**
   * Clears resources after the usage.
   *
   * The function should not block the thread.
   */
  fun close()

  /**
   * Validates if a process exit code indicates normal termination.
   *
   * Called when [ProcessExitPolicy] is [CHECK_CODE] to determine if termination should raise [IjentUnavailableException].
   * By default, only exit code 0 is considered normal.
   *
   * Common case in containerized environments: when stopping container, all processes receive SIGKILL (137).
   * To avoid false error reporting, we check container state:
   * - container.isRunning=false: normal container stop
   * - container.isRunning=true: process killed unexpectedly
   *
   * @param exitCode The exit code returned by the process
   * @return true if termination is normal, false to trigger error handling
   * @see ProcessExitPolicy
   */
  suspend fun isExpectedProcessExit(exitCode: Int): Boolean = exitCode == 0

  interface Posix : IjentDeployingStrategy {
    /** @see [IjentDeployingStrategy.getTargetPlatform] */
    override suspend fun getTargetPlatform(): EelPlatform.Posix
  }

  interface Windows : IjentDeployingStrategy {
    /** @see [IjentDeployingStrategy.getTargetPlatform] */
    override suspend fun getTargetPlatform(): EelPlatform.Windows
  }
}