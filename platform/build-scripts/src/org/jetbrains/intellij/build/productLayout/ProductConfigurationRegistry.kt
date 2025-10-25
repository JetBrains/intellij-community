// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val PRODUCT_REGISTRY_PATH = "build/dev-build.json"

@Serializable
internal data class ProductConfigurationRegistry(@JvmField val products: Map<String, ProductConfiguration>)

@Serializable
internal data class ProductConfiguration(
  @JvmField val modules: List<String>,
  @JvmField @SerialName("class") val className: String,
  @JvmField val pluginXmlPath: String? = null
)