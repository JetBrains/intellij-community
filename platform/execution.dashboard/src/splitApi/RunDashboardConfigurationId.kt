// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.UID
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class RunDashboardConfigurationId(override val uid: UID) : Id

@ApiStatus.Internal
fun RunDashboardConfigurationId.findConfigurationValue(): RunConfiguration? {
  return findValueById(this, type = RunDashboardConfigurationIdType)
}

@ApiStatus.Internal
fun RunConfiguration.storeGlobally(cs: CoroutineScope): RunDashboardConfigurationId {
  return storeValueGlobally(cs, this, RunDashboardConfigurationIdType)
}

private object RunDashboardConfigurationIdType : BackendValueIdType<RunDashboardConfigurationId, RunConfiguration>(::RunDashboardConfigurationId)