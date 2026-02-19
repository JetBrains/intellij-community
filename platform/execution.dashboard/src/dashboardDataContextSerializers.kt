// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard

import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.execution.dashboard.SELECTED_DASHBOARD_SERVICE_ID
import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import kotlinx.serialization.KSerializer

class RunDashboardServiceDataContextSerializer : CustomDataContextSerializer<RunDashboardServiceId> {
  override val key: DataKey<RunDashboardServiceId> = SELECTED_DASHBOARD_SERVICE_ID
  override val serializer: KSerializer<RunDashboardServiceId> = RunDashboardServiceId.serializer()
}

