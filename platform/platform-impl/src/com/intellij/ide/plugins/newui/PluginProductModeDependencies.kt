// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/** Supplies authoritative compatibility metadata for a known custom plugin repository. */
@ApiStatus.Internal
@ApiStatus.Experimental
interface CustomRepositoryProductModeMetadataProvider {
  fun isApplicable(plugin: PluginUiModel): Boolean

  /** A null result means that an applicable repository could not provide its authoritative metadata. */
  suspend fun loadProductModeDependencies(plugin: PluginUiModel): PluginProductModeDependencies?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<CustomRepositoryProductModeMetadataProvider> =
      ExtensionPointName.create("com.intellij.customRepositoryProductModeMetadataProvider")
  }
}

/** The product-mode portion of a plugin.xml descriptor, independent of its source repository. */
@ApiStatus.Internal
@ApiStatus.Experimental
data class PluginProductModeDependencies(
  val mainModuleDependencies: List<String>,
  val contentModules: List<ContentModuleDependencies>,
)

@ApiStatus.Internal
@ApiStatus.Experimental
data class ContentModuleDependencies(
  val loadingRule: LoadingRule,
  val moduleDependencies: List<String>,
)

@ApiStatus.Internal
@ApiStatus.Experimental
enum class LoadingRule {
  REQUIRED,
  OPTIONAL,
}
