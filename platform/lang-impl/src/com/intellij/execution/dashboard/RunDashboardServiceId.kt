// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard

import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.UID
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class RunDashboardServiceId(override val uid: UID) : Id

@ApiStatus.Internal
fun RunDashboardServiceId.findValue(): RunDashboardService? {
  return findValueById(this, type = RunDashboardServiceIdType)
}
@ApiStatus.Internal
object RunDashboardServiceIdType : BackendValueIdType<RunDashboardServiceId, RunDashboardService>(::RunDashboardServiceId)