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
    isFrontendAndSplitRegistryEnabled() -> true
    isBackendAndMonolithRegistryEnabled() -> true
    IdeProductMode.isMonolith -> true
    else -> false
  }
  fileLogger().debug("Services implementation is ${if (isServicesEnabled) "enabled" else "disabled"} in current environment. " +
                     "Is frontend: ${IdeProductMode.isFrontend}, is monolith: ${IdeProductMode.isMonolith}, is backend: ${IdeProductMode.isBackend}.")
  return isServicesEnabled
}

private fun isBackendAndMonolithRegistryEnabled(): Boolean {
  return IdeProductMode.isBackend && isOldMonolithServiceViewEnabled()
}

private fun isFrontendAndSplitRegistryEnabled(): Boolean {
  return IdeProductMode.isFrontend && isNewFrontendServiceViewEnabled()
}

@ApiStatus.Internal
fun isNewFrontendServiceViewEnabled(): Boolean {
  // Split debugger's frontend works with a frontend run dashboard entities, same for backend. So registry flags must be in sync
  // when it comes to testing either the debugger or service view.
  // Otherwise we have to maintain even more registry flag combinations compatible which does not make sense
  if (isSplitDebuggerEnabledInTestsCopyPaste()) return true

  return Registry.`is`("services.view.split.enabled")
}

@ApiStatus.Internal
fun isOldMonolithServiceViewEnabled(): Boolean {
  if (isSplitDebuggerEnabledInTestsCopyPaste()) return false

  return Registry.`is`("services.view.monolith.enabled")
}

private fun isSplitDebuggerEnabledInTestsCopyPaste(): Boolean {
  val testProperty = System.getProperty("xdebugger.toolwindow.split.for.tests")
  return testProperty?.toBoolean() ?: false
}

// dedicated key for the RUN toolwindow since it is not properly split
@ApiStatus.Internal
fun isShowLuxedRunToolwindowInServicesView(): Boolean {
  return Registry.`is`("services.view.split.run.luxing.enabled", true)
}