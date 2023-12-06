// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.target

import com.intellij.execution.target.*
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.listWindowsLocalDriveRoots
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import java.nio.file.Path
import kotlin.io.path.pathString

class WslTargetEnvironmentConfiguration() : TargetConfigurationWithId(WslTargetType.TYPE_ID),
                                            PersistentStateComponent<WslTargetEnvironmentConfiguration.MyState>,
                                            PersistentTargetEnvironmentConfiguration,
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

  override val targetId: TargetId get() = TargetId(distribution!!.id)

  override fun getTargetPathIfLocalPathIsOnTarget(probablyPathOnTarget: Path): FullPathOnTarget? {
    if (probablyPathOnTarget.root in listWindowsLocalDriveRoots()) return null
    if (!probablyPathOnTarget.pathString.startsWith("\\\\wsl")) return null
    return distribution!!.getWslPath(probablyPathOnTarget)
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
