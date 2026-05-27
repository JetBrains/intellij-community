// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.components.Service
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * Don't use this directly, use [DynamicPlugins] as an API
 */
@IntellijInternalApi
@ApiStatus.Internal
interface DynamicPluginsSupport {
  suspend fun validateDynamicTransitionPossible(targetState: PluginSet): DynamicPluginsTransitionResult.Invalid?

  suspend fun performDynamicTransition(targetState: PluginSet): DynamicPluginsTransitionResult

  companion object
}

@ApiStatus.Internal
abstract class DynamicPluginsTransitionResult private constructor() {
  /**
   * Transition is not possible due to [reason]. The state of plugins is intact.
   */
  open class Invalid(val reason: DynamicTransitionIsNotPossibleReason) : DynamicPluginsTransitionResult()

  /**
   * Transition was attempted but wasn't finished. The state of plugins is inconsistent and restart is required
   */
  open class Incomplete: DynamicPluginsTransitionResult()

  open class Success : DynamicPluginsTransitionResult()
}

@ApiStatus.Internal
interface DynamicTransitionIsNotPossibleReason {
  val logMessage: @NonNls String

  companion object {
    fun of(logMessage: @NonNls String): DynamicTransitionIsNotPossibleReason = DynamicTransitionIsNotPossibleReasonImpl(logMessage)
  }
}

private val instance: DynamicPluginsSupport? by lazy {
  // note: dynamic plugin reconfiguration is available only after app init, so Registry is available
  if (!Registry.`is`("dynamic.plugins.support.new.impl", false)) null
  else {
    val classloaderUnloadStrategy = when (Registry.get("dynamic.plugins.support.await.classloader.unload.strategy").selectedOption) {
      "asyncPostTransition" -> AwaitClassloaderUnloadAsyncPostTransition()
      "beforeLoad" -> AwaitClassloaderUnloadBeforeLoading()
      else -> AwaitClassloaderUnloadBeforeLoading()
    }
    DynamicPluginsSupportImpl(classloaderUnloadStrategy)
  }
}

@ApiStatus.Internal
fun DynamicPluginsSupport.Companion.getInstance(): DynamicPluginsSupport? = instance

private class DynamicTransitionIsNotPossibleReasonImpl(override val logMessage: @NonNls String): DynamicTransitionIsNotPossibleReason

@IntellijInternalApi
@ApiStatus.Internal
@Service
internal class DynamicPluginsSupportService(val coroutineScope: CoroutineScope)