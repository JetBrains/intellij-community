// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target

import com.intellij.execution.ExecutionException
import com.intellij.execution.target.EelTargetEnvironment
import com.intellij.execution.target.EelTargetEnvironmentRequest
import com.intellij.execution.target.ExternallySynchronized
import com.intellij.execution.target.ResolvedPortBinding
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetPlatform
import com.intellij.execution.target.TargetedCommandLine
import com.intellij.execution.target.value.TargetValue
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.applyWslOptions
import com.intellij.execution.wsl.rootMappings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.eel.EelExecPosixApi
import com.intellij.platform.eel.EelExecPosixApi.PosixEnvironmentVariablesOptions.Mode
import com.intellij.platform.eel.EelPosixApi
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.PosixEnvironmentVariablesOptionsBuilder
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApiBlocking
import com.intellij.util.asSafely
import fleet.util.logging.logger
import java.nio.file.Path

/**
 * WSL Target Environment implementation that delegates all operations to EelTargetEnvironment.
 *
 * This class serves as a bridge between the legacy WSL-specific target environment API
 * and the modern EEL API. It creates an EelTargetEnvironment
 * internally and delegates all operations to it.
 *
 * Migration Notes:
 * - Port forwarding is now handled by EelTargetEnvironment, which uses EelTunnelsApi
 * - The legacy WslProxy mechanism for WSL2 port forwarding is replaced by EEL's native tunneling
 * - Volume synchronization uses EelPathUtils.walkingTransfer instead of WslSync
 * - Process creation goes through EelApi.exec instead of WSLDistribution.patchCommandLine
 *
 * Known Differences:
 * - Original implementation had special handling for WSL1 vs WSL2 port forwarding
 * - EelTargetEnvironment handles this transparently through the tunneling API
 */
@Deprecated("Use EelTargetEnvironment instead")
class WslTargetEnvironment(
  override val request: WslTargetEnvironmentRequest,
  distribution: WSLDistribution,
) : TargetEnvironment(request), ExternallySynchronized {
  private val eelApi: EelPosixApi

  init {
    if (ApplicationManager.getApplication().isDispatchThread) {
      logger.error("WslTargetEnvironment must not be created on EDT.")
    }

    val eelApi = distribution.getUNCRootPath().getEelDescriptor().toEelApiBlocking()

    check(eelApi !is LocalEelApi) {
      "Expected WSL EEL API for distribution ${distribution.msId}, but got LocalEelApi"
    }
    this.eelApi = eelApi.asSafely<EelPosixApi>() ?: throw IllegalStateException(
      "Expected EelPosixApi for WSL distribution ${distribution.msId}, but got ${eelApi::class.java}"
    )
  }

  private val eelRequest = EelTargetEnvironmentRequest(EelTargetEnvironmentRequest.Configuration(eelApi)).apply {
    request.uploadVolumes.forEach { uploadRoot ->
      this.uploadVolumes.add(uploadRoot)
    }

    request.downloadVolumes.forEach { downloadRoot ->
      this.downloadVolumes.add(downloadRoot)
    }

    request.targetPortBindings.forEach { portBinding ->
      this.targetPortBindings.add(portBinding)
    }

    request.localPortBindings.forEach { portBinding ->
      this.localPortBindings.add(portBinding)
    }
  }

  private val delegate = EelTargetEnvironment(eelRequest)

  override val synchronizedVolumes: List<SynchronizedVolume> = distribution.rootMappings.map {
    SynchronizedVolume(Path.of(it.localRoot), it.remoteRoot)
  }

  override val uploadVolumes: Map<UploadRoot, UploadableVolume> get() = delegate.uploadVolumes
  override val downloadVolumes: Map<DownloadRoot, DownloadableVolume> get() = delegate.downloadVolumes
  override val targetPortBindings: Map<TargetPortBinding, ResolvedPortBinding> get() = delegate.targetPortBindings
  override val localPortBindings: Map<LocalPortBinding, ResolvedPortBinding> get() = delegate.localPortBindings
  override val targetPlatform: TargetPlatform get() = delegate.targetPlatform

  @Throws(ExecutionException::class)
  override fun createProcess(commandLine: TargetedCommandLine, indicator: ProgressIndicator): Process {
    val (command, envs) = runBlockingCancellable {
      applyWslOptions(
        commandLine.collectCommandsSynchronously(),
        commandLine.environmentVariables,
        eelApi.exec,
        request.wslOptions
      )
    }
    val resultCommandLine = commandLine.toBuilder(request).also { builder ->
      builder.setExePath(command.first())
      builder.setParameters(command.drop(1).map {
        TargetValue.fixed(it)
      })
      builder.setEnvironmentVariables(envs.mapValues { (_, value) ->
        TargetValue.fixed(value)
      })
    }.build()
    return delegate.createProcess(resultCommandLine, parentEnvVarsOptions())
  }

  private fun parentEnvVarsOptions(): EelExecPosixApi.PosixEnvironmentVariablesOptions {
    // The command should be executed with login non-interactive environment variables by default.
    // Or with login interactive environment variables if `wslOptions.isExecuteCommandInInteractiveShell` is enabled.
    val mode = if (request.wslOptions.isExecuteCommandInShell && request.wslOptions.isExecuteCommandInLoginShell) {
      // Here the command is executed in a login shell, like `/usr/bin/bash -l -c "command"`
      // => no need to fetch the login env vars on the IDE side.
      // Moreover, spawning with the login env vars may influence login shell startup in unpredictable ways.
      Mode.MINIMAL
    }
    else {
      Mode.LOGIN_NON_INTERACTIVE
    }
    return PosixEnvironmentVariablesOptionsBuilder().mode(mode).build()
  }

  override fun shutdown() {
    delegate.shutdown()
  }

  private companion object {
    private val logger = logger<WslTargetEnvironment>()
  }
}
