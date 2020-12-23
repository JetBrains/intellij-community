// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WSLUtil
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.util.concurrency.SynchronizedClearableLazy

class WslTargetEnvironmentConfiguration() : TargetEnvironmentConfiguration(WslTargetType.TYPE_ID),
                                            PersistentStateComponent<WslTargetEnvironmentConfiguration.MyState> {
  private var distributionMsId: String? = null
  private val distributionLazy: SynchronizedClearableLazy<WSLDistribution?> = SynchronizedClearableLazy {
    distributionMsId ?: return@SynchronizedClearableLazy null
    WSLUtil.getAvailableDistributions().first { it.msId === distributionMsId }
  }
  var distribution: WSLDistribution?
    get() = distributionLazy.value
    set(value) {
      distributionMsId = value?.msId
      distributionLazy.drop()
    }

  constructor(initialDistribution: WSLDistribution?) : this() {
    distributionMsId = initialDistribution?.msId
  }

  override fun getState() = MyState().also {
    it.distributionMsId = distributionMsId
  }

  override fun loadState(state: MyState) {
    distributionMsId = state.distributionMsId
    distributionLazy.drop()
  }

  class MyState : BaseState() {
    var distributionMsId by string()
  }
}
