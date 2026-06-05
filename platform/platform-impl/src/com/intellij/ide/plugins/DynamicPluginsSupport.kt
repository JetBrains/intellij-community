// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.components.Service
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * Don't use this directly, use [DynamicPlugins] as an API
 */
@ApiStatus.Internal
interface DynamicPluginsSupport {
  suspend fun validateDynamicReconfigurationPossible(targetState: PluginSet): DynamicPluginsReconfigurationResult.Invalid?

  suspend fun performDynamicReconfiguration(targetState: PluginSet): DynamicPluginsReconfigurationResult

  // TODO consider adding suspend fun runPreventingDynamicReconfiguration(body: suspend? () -> Unit)

  companion object
}

@ApiStatus.Internal
abstract class DynamicPluginsReconfigurationResult private constructor() {
  /**
   * Reconfiguration is not possible due to [reason]. The state of plugins is intact.
   */
  open class Invalid(val reason: DynamicReconfigurationIsNotPossibleReason) : DynamicPluginsReconfigurationResult()

  /**
   * Reconfiguration was attempted but wasn't finished. The state of plugins is inconsistent and restart is required
   */
  open class Incomplete: DynamicPluginsReconfigurationResult()

  open class Success : DynamicPluginsReconfigurationResult()
}

@ApiStatus.Internal
interface DynamicReconfigurationIsNotPossibleReason {
  val logMessage: @NonNls String
  val problematicPlugin: PluginMainDescriptor?

  companion object {
    fun of(logMessage: @NonNls String, problematicPlugin: PluginMainDescriptor?): DynamicReconfigurationIsNotPossibleReason =
      DynamicReconfigurationIsNotPossibleReasonImpl(logMessage, problematicPlugin)
  }
}

private val instance: DynamicPluginsSupport? by lazy {
  // note: dynamic plugin reconfiguration is available only after app init, so Registry is available
  if (!Registry.`is`("dynamic.plugins.support.new.impl", false)) null
  else {
    val classloaderUnloadStrategy = when (Registry.get("dynamic.plugins.support.await.classloader.unload.strategy").selectedOption) {
      "asyncPostReconfiguration" -> AwaitClassloaderUnloadAsyncPostReconfiguration()
      "beforeLoad" -> AwaitClassloaderUnloadBeforeLoading()
      else -> AwaitClassloaderUnloadBeforeLoading()
    }
    DynamicPluginsSupportImpl(classloaderUnloadStrategy)
  }
}

@ApiStatus.Internal
fun DynamicPluginsSupport.Companion.getInstance(): DynamicPluginsSupport? = instance

private class DynamicReconfigurationIsNotPossibleReasonImpl(
  override val logMessage: @NonNls String,
  override val problematicPlugin: PluginMainDescriptor?,
): DynamicReconfigurationIsNotPossibleReason

@ApiStatus.Internal
@Service
internal class DynamicPluginsSupportService(val coroutineScope: CoroutineScope)