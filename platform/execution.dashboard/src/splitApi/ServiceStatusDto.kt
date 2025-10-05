// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi

import com.intellij.execution.dashboard.RunDashboardServiceId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class ServiceStatusDto(
  val id: RunDashboardServiceId,
  val statusId: String,
)