// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target

import com.intellij.execution.target.*
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.rootMappings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApiBlocking
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
  private val eelApi: EelApi

  init {
    if (ApplicationManager.getApplication().isDispatchThread) {
      logger.error("WslTargetEnvironment must not be created on EDT.")
    }

    eelApi = distribution.getUNCRootPath().getEelDescriptor().toEelApiBlocking()

    check(eelApi !is LocalEelApi) {
      "Expected WSL EEL API for distribution ${distribution.msId}, but got LocalEelApi"
    }
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

    this.shouldCopyVolumes = (request as? VolumeCopyingRequest)?.shouldCopyVolumes ?: false
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

  override fun createProcess(commandLine: TargetedCommandLine, indicator: ProgressIndicator): Process {
    return delegate.createProcess(commandLine, indicator)
  }

  override fun shutdown() {
    delegate.shutdown()
  }

  private companion object {
    private val logger = logger<WslTargetEnvironment>()
  }
}
