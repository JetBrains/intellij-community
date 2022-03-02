// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.target

import com.intellij.execution.ExecutionException
import com.intellij.execution.Platform
import com.intellij.execution.process.LocalPtyOptions
import com.intellij.execution.target.*
import com.intellij.execution.target.TargetEnvironment.*
import com.intellij.execution.target.value.TargetValue
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.ide.IdeBundle

class WslTargetEnvironmentRequest : BaseTargetEnvironmentRequest {
  override val configuration: WslTargetEnvironmentConfiguration
  var ptyOptions: LocalPtyOptions? = null
  val wslOptions: WSLCommandLineOptions = WSLCommandLineOptions()

  constructor(config: WslTargetEnvironmentConfiguration) {
    this.configuration = config
  }

  private constructor(config: WslTargetEnvironmentConfiguration,
                      uploadVolumes: MutableSet<UploadRoot>,
                      downloadVolumes: MutableSet<DownloadRoot>,
                      targetPortBindings: MutableSet<TargetPortBinding>,
                      localPortBindings: MutableSet<LocalPortBinding>) : super(uploadVolumes, downloadVolumes, targetPortBindings,
                                                                               localPortBindings) {
    this.configuration = config
  }

  override fun duplicate(): WslTargetEnvironmentRequest {
    return WslTargetEnvironmentRequest(configuration,
                                       HashSet(uploadVolumes),
                                       HashSet(downloadVolumes),
                                       HashSet(targetPortBindings),
                                       HashSet(localPortBindings))
  }

  override val targetPlatform: TargetPlatform
    get() = TargetPlatform(Platform.UNIX)

  override val defaultVolume: TargetEnvironmentRequest.Volume
    get() {
      throw UnsupportedOperationException("defaultVolume is not implemented")
    }

  override fun createUploadRoot(remoteRootPath: String?, temporary: Boolean): TargetEnvironmentRequest.Volume {
    throw UnsupportedOperationException("createUploadRoot is not implemented")
  }

  override fun createDownloadRoot(remoteRootPath: String?): TargetEnvironmentRequest.DownloadableVolume {
    throw UnsupportedOperationException("createDownloadRoot is not implemented")
  }

  override fun bindTargetPort(targetPort: Int): TargetValue<Int> {
    return TargetValue.fixed(targetPort)
  }

  override fun bindLocalPort(localPort: Int): TargetValue<HostPort> {
    return TargetValue.fixed(HostPort("localhost", localPort))
  }

  @Throws(ExecutionException::class)
  override fun prepareEnvironment(progressIndicator: TargetProgressIndicator): TargetEnvironment {
    val distribution = configuration.distribution
    if (distribution == null) {
      throw ExecutionException(IdeBundle.message("wsl.no.distribution.found.error"))
    }
    return WslTargetEnvironment(this, distribution).also { environmentPrepared(it, progressIndicator) }
  }
}
