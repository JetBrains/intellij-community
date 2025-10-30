// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.platform.ide.productMode.IdeProductMode
import org.jetbrains.annotations.ApiStatus
import java.util.*

private val LOG  by lazy {
  fileLogger()
}

@ApiStatus.Internal
// FIXME: When we consider client services stable enough, all usages of the method must be removed,
//        and the corresponding classes must be moved to frontend modules of their plugins
fun shouldEnableServicesViewInCurrentEnvironment(): Boolean {
  val isServicesEnabled = when {
    IdeProductMode.isMonolith -> true
    IdeProductMode.isFrontend && isNewFrontendServiceViewEnabled() -> true
    IdeProductMode.isBackend && isOldMonolithServiceViewEnabled() -> true
    else -> false
  }
  LOG.debug("Services implementation is ${if (isServicesEnabled) "enabled" else "disabled"} in current environment. " +
            "Is frontend: ${IdeProductMode.isFrontend}, is monolith: ${IdeProductMode.isMonolith}, is backend: ${IdeProductMode.isBackend}.")
  return isServicesEnabled
}

@ApiStatus.Internal
fun isNewFrontendServiceViewEnabled(): Boolean {
  // Split debugger's frontend works with a frontend run dashboard entities, same for backend. So registry flags must be in sync
  // when it comes to testing either the debugger or service view.
  // Otherwise we have to maintain even more registry flag combinations compatible which does not make sense
  if (isSplitDebuggerEnabledInTestsCopyPaste()) return true
  if (IdeProductMode.isMonolith) return false
  return isSplitServicesRegistryFlagOn() && isCurrentProductSupportSplitServiceView()
}

@ApiStatus.Internal
fun isOldMonolithServiceViewEnabled(): Boolean {
  if (isSplitDebuggerEnabledInTestsCopyPaste()) return false
  if (!isCurrentProductSupportSplitServiceView()) return true

  return IdeProductMode.isMonolith || !isSplitServicesRegistryFlagOn()
}

private fun isSplitDebuggerEnabledInTestsCopyPaste(): Boolean {
  val testProperty = System.getProperty("xdebugger.toolwindow.split.for.tests")
  return testProperty?.toBoolean() ?: false
}

private fun isSplitServicesRegistryFlagOn(): Boolean {
  return shouldEnableSplitServiceViewCachedRegistryValue
}

// dedicated key for the RUN toolwindow since it is not properly split
@ApiStatus.Internal
fun isShowLuxedRunToolwindowInServicesView(): Boolean {
  return shouldEnableLuxedRunToolwindowInServiceViewCachedRegistryValue
}

private val shouldEnableSplitServiceViewCachedRegistryValue by lazy {
  Registry.`is`("services.view.split.enabled", true)
}

private val shouldEnableLuxedRunToolwindowInServiceViewCachedRegistryValue by lazy {
  Registry.`is`("services.view.split.run.luxing.enabled", true)
}

// mutating state of current services implementation
@ApiStatus.Internal
fun isCurrentProductSupportSplitServiceView(): Boolean {
  val value = getValueIfExists("services.view.split.products")?.asString() ?: return false
  val productCodes = value.split(",").toSet()
  val currentProductCode = if (IdeProductMode.isFrontend) {
    ApplicationInfoEx.getInstanceEx().fullIdeProductCode
  }
  else {
    ApplicationInfoImpl.getShadowInstanceImpl().build.productCode
  }
  return currentProductCode in productCodes
}

@ApiStatus.Internal
fun setServiceViewImplementationForNextIdeRun(shouldEnableSplitImplementation: Boolean) {
  getValueIfExists("services.view.split.enabled")?.setValue(shouldEnableSplitImplementation)
  getValueIfExists("services.view.split.run.luxing.enabled")?.setValue(shouldEnableSplitImplementation)

  if (shouldEnableSplitImplementation) {
    // do not disable debugger since it is a separate functionality, only enable it if services are enabled as well
    getValueIfExists("xdebugger.toolwindow.split.remdev")?.setValue(true)
  }
  getValueIfExists("docker.split.service.view.enabled")?.setValue(shouldEnableSplitImplementation)
  getValueIfExists("docker.registry.split.service.view.enabled")?.setValue(shouldEnableSplitImplementation)
}

@ApiStatus.Internal
fun getServiceViewRegistryFlagsState(): Map<String, Boolean> {
  return mapOf(
    "services.view.split.enabled" to Registry.`is`("services.view.split.enabled", true),
    "services.view.split.run.luxing.enabled" to Registry.`is`("services.view.split.run.luxing.enabled", true),
    "xdebugger.toolwindow.split.remdev" to Registry.`is`("xdebugger.toolwindow.split.remdev", true),
    "docker.split.service.view.enabled" to Registry.`is`("docker.split.service.view.enabled", true),
    "docker.registry.split.service.view.enabled" to Registry.`is`("docker.registry.split.service.view.enabled", true),
  )
}

private fun getValueIfExists(key: String): RegistryValue? {
  val maybeMissingValue = Registry.get(key)
  return try {
    maybeMissingValue.asString()
    maybeMissingValue
  }
  catch (_: MissingResourceException) {
    null
  }
}