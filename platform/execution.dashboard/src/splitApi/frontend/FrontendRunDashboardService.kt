// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi.frontend

import com.intellij.platform.execution.dashboard.splitApi.RunDashboardServiceDto
import org.jetbrains.annotations.ApiStatus

/**
 * This class is used in service contributor to preserve selection when a run configuration is launched,
 * which relays on overridden equals/hashCode methods.
 */
@ApiStatus.Internal
class FrontendRunDashboardService(val runDashboardServiceDto: RunDashboardServiceDto) {

  // implementation detail: custom hashcode/equals are necessary to preserve selection when a run configuration is launched
  override fun equals(other: Any?): Boolean {
    return other is FrontendRunDashboardService &&
           other.runDashboardServiceDto.javaClass == runDashboardServiceDto.javaClass &&
           other.runDashboardServiceDto.uuid == runDashboardServiceDto.uuid
  }

  override fun hashCode(): Int {
    return runDashboardServiceDto.uuid.hashCode()
  }
}