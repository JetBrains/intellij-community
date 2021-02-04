// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent

class WslTargetEnvironmentConfiguration() : TargetEnvironmentConfiguration(WslTargetType.TYPE_ID),
                                            PersistentStateComponent<WslTargetEnvironmentConfiguration.MyState> {
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

  class MyState : BaseState() {
    var distributionMsId by string()
    var projectRootOnTarget by string()
  }
}
