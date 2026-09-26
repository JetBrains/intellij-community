// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.featureStatistics.fusCollectors

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class SessionType {
  LIGHT_ONLY,
  LIGHT_THEN_SMART,
  SMART_ONLY,
}

@ApiStatus.Internal
interface IdeShutdownSessionTypeProvider {
  fun sessionType(): SessionType

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<IdeShutdownSessionTypeProvider> =
      ExtensionPointName.create("com.intellij.statistic.eventLog.ideShutdownSessionTypeProvider")
  }
}
