// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target

import com.intellij.execution.Platform
import com.intellij.execution.target.BaseTargetEnvironmentRequest
import com.intellij.execution.target.HostPort
import com.intellij.execution.target.TargetEnvironment.*
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetPlatform
import com.intellij.execution.target.value.TargetValue
import java.util.*

class WslTargetEnvironmentRequest : BaseTargetEnvironmentRequest {
  val config: WslTargetEnvironmentConfiguration

  constructor(config: WslTargetEnvironmentConfiguration) {
    this.config = config
  }

  private constructor(config: WslTargetEnvironmentConfiguration,
                      uploadVolumes: MutableSet<UploadRoot>,
                      downloadVolumes: MutableSet<DownloadRoot>,
                      targetPortBindings: MutableSet<TargetPortBinding>,
                      localPortBindings: MutableSet<LocalPortBinding>) : super(uploadVolumes, downloadVolumes, targetPortBindings,
                                                                               localPortBindings) {
    this.config = config
  }

  override fun duplicate(): WslTargetEnvironmentRequest {
    return WslTargetEnvironmentRequest(config,
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
}
