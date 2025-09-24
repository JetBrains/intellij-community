// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.productMode.IdeProductMode
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
// FIXME: When we consider client services stable enough, all usages of the method must be removed,
//        and the corresponding classes must be moved to frontend modules of their plugins
fun shouldEnableServicesViewInCurrentEnvironment(): Boolean {
  val isServicesEnabled = when {
    IdeProductMode.isFrontend && isNewFrontendServiceViewEnabled() -> true
    IdeProductMode.isBackend && isOldMonolithServiceViewEnabled() -> true
    IdeProductMode.isMonolith -> true
    else -> false
  }
  fileLogger().debug("Services implementation is ${if (isServicesEnabled) "enabled" else "disabled"} in current environment. " +
                     "Is frontend: ${IdeProductMode.isFrontend}, is monolith: ${IdeProductMode.isMonolith}, is backend: ${IdeProductMode.isBackend}.")
  return isServicesEnabled
}

private fun isNewFrontendServiceViewEnabled(): Boolean {
  return Registry.`is`("services.view.split.enabled") || Registry.`is`("xdebugger.toolwindow.split.remdev")
}

private fun isOldMonolithServiceViewEnabled(): Boolean {
  return Registry.`is`("services.view.monolith.enabled")
}