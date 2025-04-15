// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
// FIXME: When we consider client services stable enough, all usages of the method must be removed,
//        and the corresponding classes must be moved to frontend modules of their plugins
fun shouldEnableServicesViewInCurrentEnvironment(): Boolean {
  return when {
    isClient() && isFrontendServiceViewEnabled() -> true
    !isClient() && isMonolithServiceViewEnabled() -> true
    else -> {
      fileLogger().debug("Services implementation is disabled in current environment. Is client: ${isClient()}")
      false
    }
  }
}

private fun isClient(): Boolean {
  return PlatformUtils.isJetBrainsClient()
}

private fun isFrontendServiceViewEnabled(): Boolean {
  return Registry.`is`("services.view.split.enabled")
}

private fun isMonolithServiceViewEnabled(): Boolean {
  return Registry.`is`("services.view.monolith.enabled")
}