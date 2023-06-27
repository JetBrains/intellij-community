// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.target

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.PersistentTargetEnvironmentConfiguration
import com.intellij.execution.target.TargetConfigurationWithLocalFsAccess
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.readableFs.PathInfo
import com.intellij.execution.target.readableFs.TargetConfigurationReadableFs
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.listWindowsLocalDriveRoots
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfoRt
import com.sun.jna.platform.win32.Kernel32.*
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.pathString

class WslTargetEnvironmentConfiguration() : TargetEnvironmentConfiguration(WslTargetType.TYPE_ID),
                                            PersistentStateComponent<WslTargetEnvironmentConfiguration.MyState>,
                                            PersistentTargetEnvironmentConfiguration,
                                            TargetConfigurationReadableFs,
                                            TargetConfigurationWithLocalFsAccess {

  override val asTargetConfig: TargetEnvironmentConfiguration = this

  override val isPersistent: Boolean = true

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


  override fun getTargetPathIfLocalPathIsOnTarget(probablyPathOnTarget: Path): FullPathOnTarget? {
    if (probablyPathOnTarget.root in listWindowsLocalDriveRoots()) return null
    if (!probablyPathOnTarget.pathString.startsWith("\\\\wsl")) return null
    return distribution!!.getWslPath(probablyPathOnTarget.pathString)
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
    // TODO: 9P is unreliable and we must migrate to some tool running in WSL (like ijent)
    assert(SystemInfoRt.isWindows) { "WSL is for Windows only" }
    val distribution = distribution
    if (distribution == null) {
      thisLogger().warn("No distribution, cant check path")
      return null
    }
    val winLocalPath = Paths.get(distribution.getWindowsPath(targetPath))
    val fileAttributes = INSTANCE.GetFileAttributes(winLocalPath.pathString)
    // Reparse point is probably symlink, but could be dir or file. See https://github.com/microsoft/WSL/issues/5118
    if (fileAttributes != INVALID_FILE_ATTRIBUTES && fileAttributes.and(FILE_ATTRIBUTE_REPARSE_POINT) == FILE_ATTRIBUTE_REPARSE_POINT) {
      return PathInfo.Unknown
    }
    val pathInfo = PathInfo.getPathInfoForLocalPath(winLocalPath)
    // We can't check if file is executable or not (we could, but it is too heavy), so we set this flag
    return if (pathInfo is PathInfo.RegularFile) pathInfo.copy(executable = true) else pathInfo
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as WslTargetEnvironmentConfiguration

    return distributionMsId?.lowercase() == other.distributionMsId?.lowercase()
  }

  override fun hashCode(): Int {
    return distributionMsId?.hashCode() ?: 0
  }

  class MyState : BaseState() {
    var distributionMsId by string()
    var projectRootOnTarget by string()
  }
}
