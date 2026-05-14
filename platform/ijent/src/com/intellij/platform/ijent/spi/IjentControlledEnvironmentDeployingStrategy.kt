// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.eel.EelPlatform
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
   * Provider of the local IJent executable runnable on [targetPlatform].
   *
   * Its [IjentExecFileProvider.getIjentBinary] is invoked once per [createIjentSession] invocation,
   * after [getTargetPlatform] and before [copyFile]; the returned file is uploaded to the target
   * environment via [copyFile] and then executed via [createProcess]. Implementations must return
   * a binary whose OS and CPU architecture match [targetPlatform]; mismatches will surface only at
   * process launch on the remote side. The lookup may suspend for a long time (e.g., to download a
   * missing binary or prompt the user) and throws [com.intellij.platform.ijent.IjentMissingBinary]
   * if no compatible binary can be produced.
   */
  protected abstract val ijentExecFileProvider: IjentExecFileProvider

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

  override suspend fun createIjentSession(provider: IjentSessionProvider): IjentSession.Posix =
    try {
      val targetPlatform = getTargetPlatform()
      val connectionStrategy = getConnectionStrategy()
      val remotePathToBinary = copyFile(ijentExecFileProvider.getIjentBinary(targetPlatform))
      val mediator = createProcess(remotePathToBinary)

      provider.connect(IjentConnectionContext(
        mediator = mediator,
        targetPlatform = targetPlatform,
        remoteBinaryPath = remotePathToBinary,
        connectionStrategy = connectionStrategy,
      )) as IjentSession.Posix
    }
    finally {
      close()
    }
}