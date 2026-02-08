// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentExecFileProvider
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.IjentUnavailableException
import com.intellij.platform.ijent.spi.IjentSessionProcessMediator.ProcessExitPolicy
import com.intellij.platform.ijent.spi.IjentSessionProcessMediator.ProcessExitPolicy.CHECK_CODE
import java.nio.file.Path

/**
 * Strategy for deploying and launching IJent in an environment where the copying and launching arbitrary executables is allowed.
 *
 * The strategy does the following steps:
 *  1. Copying the IJent binary to the target environment (see [copyFile])
 *  2. Launching the IJent process (see [createProcess]) with the correct arguments
 *  3. Creating an [IjentSession] using process's stdin/out
 */
abstract class IjentControlledEnvironmentDeployingStrategy : IjentDeployingStrategy {
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
  protected abstract suspend fun createProcess(binaryPath: String): IjentSessionProcessMediator

  /**
   * Copy files to the target environment. Typically used to transfer the ijent binary to the target machine.
   *
   * @param file path to local file that should be copied to target environment.
   */
  protected abstract suspend fun copyFile(file: Path): String

  /**
   * Clears resources after the usage.
   *
   * The function should not block the thread.
   */
  protected abstract fun close()

  /**
   * Returns the target platform where IJent will be deployed.
   */
  protected abstract suspend fun getTargetPlatform(): EelPlatform

  /**
   * Returns the connection strategy for communicating with IJent.
   */
  protected abstract suspend fun getConnectionStrategy(): IjentConnectionStrategy

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
  open suspend fun isExpectedProcessExit(exitCode: Int): Boolean = exitCode == 0

  final override suspend fun <T : IjentApi> createIjentSession(): IjentSession<T> =
    try {
      val targetPlatform = getTargetPlatform()
      val connectionStrategy = getConnectionStrategy()
      val remotePathToBinary = copyFile(IjentExecFileProvider.getInstance().getIjentBinary(targetPlatform))
      val mediator = createProcess(remotePathToBinary)

      createIjentSession(IjentConnectionContext(
        mediator = mediator,
        targetPlatform = targetPlatform,
        remoteBinaryPath = remotePathToBinary,
        connectionStrategy = connectionStrategy,
      ))
    }
    finally {
      close()
    }
}