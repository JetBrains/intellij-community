// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target

import com.intellij.execution.Platform
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState.TargetProgressIndicator
import com.intellij.execution.target.TargetEnvironmentFactory
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetPlatform
import com.intellij.ide.IdeBundle

class WslTargetEnvironmentFactory(private val myConfig: WslTargetEnvironmentConfiguration) : TargetEnvironmentFactory {
  override fun getTargetConfiguration(): WslTargetEnvironmentConfiguration {
    return myConfig
  }

  override fun getTargetPlatform(): TargetPlatform {
    return TargetPlatform(Platform.UNIX)
  }

  override fun createRequest(): TargetEnvironmentRequest {
    return WslTargetEnvironmentRequest(myConfig)
  }

  override fun prepareRemoteEnvironment(request: TargetEnvironmentRequest,
                                        targetProgressIndicator: TargetProgressIndicator): TargetEnvironment {
    val wslRequest = request as WslTargetEnvironmentRequest
    val distribution = wslRequest.config.distribution
    if (distribution == null) {
      error(IdeBundle.message("wsl.no.distribution.found.error"))
    }
    return WslTargetEnvironment(wslRequest, distribution)
  }
}
