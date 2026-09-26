// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi

import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.dashboard.RunDashboardServiceId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
class RunDashboardLuxedContentEvent(
  val serviceId: RunDashboardServiceId,
  val contentDescriptorId: RunContentDescriptorIdImpl,
  val executorId: String,
  val isAdded: Boolean,
)