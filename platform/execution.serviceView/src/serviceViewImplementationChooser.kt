// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.idea.AppMode
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
// FIXME: When we consider client services stable enough, all usages of the method must be removed,
//        and the corresponding classes must be moved to frontend modules of their plugins
fun shouldEnableServicesViewInCurrentEnvironment(): Boolean {
  val isServicesEnabled = when {
    isFrontend() && isNewFrontendServiceViewEnabled() -> true
    isBackend() && isOldMonolithServiceViewEnabled() -> true
    isMonolith() -> true
    else -> false
  }
  fileLogger().debug("Services implementation is ${if (isServicesEnabled) "enabled" else "disabled"} in current environment. " +
                     "Is frontend: ${isFrontend()}, is monolith: ${isMonolith()}, is backend: ${isBackend()}.")
  return isServicesEnabled
}

private fun isFrontend(): Boolean {
  return PlatformUtils.isJetBrainsClient()
}

private fun isMonolith(): Boolean {
  // returns true in split mode backend 0_o
  return FrontendApplicationInfo.getFrontendType() is FrontendType.Monolith && !isBackend() && !isFrontend()
}

private fun isBackend(): Boolean {
  return AppMode.isRemoteDevHost()
}

private fun isNewFrontendServiceViewEnabled(): Boolean {
  return Registry.`is`("services.view.split.enabled")
}

private fun isOldMonolithServiceViewEnabled(): Boolean {
  return Registry.`is`("services.view.monolith.enabled")
}