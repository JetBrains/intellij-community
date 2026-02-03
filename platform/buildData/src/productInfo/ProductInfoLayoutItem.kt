// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildData.productInfo

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class ProductInfoLayoutItem(
  @JvmField val name: String,
  @JvmField val kind: ProductInfoLayoutItemKind,
  @JvmField val classPath: List<String> = emptyList(),
)

@Suppress("EnumEntryName")
@ApiStatus.Internal
@Serializable
enum class ProductInfoLayoutItemKind {
  plugin, pluginAlias, productModuleV2, moduleV2
}