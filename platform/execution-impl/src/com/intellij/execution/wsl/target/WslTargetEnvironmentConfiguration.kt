// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target

import com.intellij.execution.Platform
import com.intellij.execution.target.TargetConfigurationWithLocalFsAccess
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetPlatform
import com.intellij.execution.target.readableFs.PathInfo
import com.intellij.execution.target.readableFs.TargetConfigurationReadableFs
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.synchronizedVolumes
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.diagnostic.thisLogger
import java.nio.file.Paths

class WslTargetEnvironmentConfiguration() : TargetEnvironmentConfiguration(WslTargetType.TYPE_ID),
                                            PersistentStateComponent<WslTargetEnvironmentConfiguration.MyState>,
                                            TargetConfigurationReadableFs,
                                            TargetConfigurationWithLocalFsAccess {

  override val synchronizedVolumes: List<TargetEnvironment.SynchronizedVolume> get() = distribution!!.synchronizedVolumes

  override val platform: TargetPlatform = TargetPlatform(Platform.UNIX)

  override val asTargetConfig: TargetEnvironmentConfiguration = this

  private var distributionMsId: String? = null
  var distribution: WSLDistribution?
    get() {
      val msId = distributionMsId ?: return null
      return WslDistributionManager.getInstance().getOrCreateDistributionByMsId(msId)
    }
    set(value) {
      distributionMsId = value?.msId
    }
  override var projectRootOnTarget: String = ""

  constructor(initialDistribution: WSLDistribution?) : this() {
    distributionMsId = initialDistribution?.msId
  }

  override fun getState() = MyState().also {
    it.distributionMsId = distributionMsId
    it.projectRootOnTarget = projectRootOnTarget
  }

  override fun loadState(state: MyState) {
    distributionMsId = state.distributionMsId
    projectRootOnTarget = state.projectRootOnTarget ?: ""
  }

  override fun toString(): String {
    val distributionIdText = distributionMsId?.let { "'$it'" }
    return "WslTargetEnvironmentConfiguration(distributionId=$distributionIdText, projectRootOnTarget='$projectRootOnTarget')"
  }

  override fun getPathInfo(targetPath: String): PathInfo? {
    val distribution = distribution
    if (distribution == null) {
      thisLogger().warn("No distribution, cant check path")
      return null
    }
    val pathInfo = PathInfo.getPathInfoForLocalPath(Paths.get(distribution.getWindowsPath(targetPath)))
    // We can't check if file is executable or not (we could but it is too heavy), so we set this flag
    return if (pathInfo is PathInfo.RegularFile) pathInfo.copy(executable = true) else pathInfo
  }

  class MyState : BaseState() {
    var distributionMsId by string()
    var projectRootOnTarget by string()
  }
}
