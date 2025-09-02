// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Marketplace vendor details.
 */
@Serializable
@ApiStatus.Internal
data class PluginNodeVendorDetails(
  @NlsSafe val name: String,
  val url: String? = null,
  private val isTrader: Boolean? = null,
  private val isVerified: Boolean? = null
) {
  fun isTrader(): Boolean {
    return isTrader ?: false
  }

  fun isVerified(): Boolean {
    return isVerified ?: false
  }
}