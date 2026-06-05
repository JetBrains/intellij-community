// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * Acts as the only source of [PluginInitializationContext] instances that are used to initialize
 * or dynamically reconfigure plugins in the current application.
 *
 * NOTE: if you want to make changes to this class or to the actual instance that is being used in the IDEs,
 * you must get an approval from the IntelliJ Platform team (use `#ij-plugin-model` channel in slack).
 */
@ApiStatus.Internal
interface PluginInitContextFactory {
  /**
   * Produces an instance of [PluginInitializationContext] that incorporates the current application settings in regard to plugin configuration
   * (e.g., this includes the current list of plugins that are marked disabled).
   * The instance is expected to be immutable. TODO right now it is not yet always so (see [ProductPluginInitContext]), but it is a desired contract, so take it into account.
   */
  fun createActualContext(): PluginInitializationContext

  /**
   * The context that is used to determine the effective module loading rule for content modules.
   * FIXME this method shouldn't exist, the code should be either aware that the effective module loading rule may depend on the current context,
   *    or perhaps the module loading rule determination should not rely on the whole context but only on an fully immutable set of env-configured modules.
   */
  fun getContextForEffectiveModuleLoadingRuleDetermination(): PluginInitializationContext

  /**
   * This instance is never used (and must never be used) for actual initialization/reconfiguration, it may only be used
   * for preliminary investigation of a potential final state of plugins if such changes were to be applied.
   */
  fun createMockContextWithOverrides(
    buildNumberOverride: BuildNumber? = null,
    disabledPluginsOverride: Set<PluginId>? = null,
    expiredPluginsOverride: Set<PluginId>? = null,
    brokenPluginVersionsOverride: Map<PluginId, Set<String>>? = null,
  ): PluginInitializationContext

  companion object {
    fun getInstance(): PluginInitContextFactory = PluginInitContextFactoryHolder.getInstance()
  }
}

/**
 * NOTE: if you want to make changes to this class or to the [PluginInitContextFactory] instance that is being used,
 * you must get an approval from the IntelliJ Platform team (use `#ij-plugin-model` channel in slack).
 */
private class ProductPluginInitContextFactory : PluginInitContextFactory {
  override fun createActualContext(): PluginInitializationContext {
    return ProductPluginInitContext()
  }

  override fun getContextForEffectiveModuleLoadingRuleDetermination(): PluginInitializationContext {
    return moduleRuleDeterminationContext
  }

  override fun createMockContextWithOverrides(
    buildNumberOverride: BuildNumber?,
    disabledPluginsOverride: Set<PluginId>?,
    expiredPluginsOverride: Set<PluginId>?,
    brokenPluginVersionsOverride: Map<PluginId, Set<String>>?,
  ): PluginInitializationContext {
    return ProductPluginInitContext(
      buildNumberOverride = buildNumberOverride,
      disabledPluginsOverride = disabledPluginsOverride,
      expiredPluginsOverride = expiredPluginsOverride,
      brokenPluginVersionsOverride = brokenPluginVersionsOverride,
    )
  }

  private companion object {
    // outside the class so that it is initialized only on the first access, and using lazy property may be costly
    private val moduleRuleDeterminationContext = ProductPluginInitContext()
  }
}

private object PluginInitContextFactoryHolder {
  private val prodInstance = ProductPluginInitContextFactory()
  var testInstance: PluginInitContextFactory? = null

  fun getInstance(): PluginInitContextFactory = testInstance ?: prodInstance
}

@ApiStatus.Internal
@TestOnly
fun <R> PluginInitContextFactory.Companion.withCustomFactoryInUnitTests(instance: PluginInitContextFactory, body: () -> R): R {
  PluginInitContextFactoryHolder.testInstance = instance
  try {
    return body()
  }
  finally {
    PluginInitContextFactoryHolder.testInstance = null
  }
}