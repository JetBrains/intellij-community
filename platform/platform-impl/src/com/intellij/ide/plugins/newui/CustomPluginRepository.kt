// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class CustomPluginRepository(
  val id: @NlsSafe String,
  val source: PluginSource,
) {
  init {
    require(id.isNotBlank()) { "Custom plugin repository ID must not be blank" }
  }
}

@ApiStatus.Internal
data class CustomPluginRepositoryLoadResult(
  val plugins: List<PluginUiModel>,
  val error: String? = null,
)
