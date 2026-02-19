// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace

import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
data class CheckErrorsResult(
  val isDisabledDependencyError: Boolean = false,
  @NlsSafe val loadingError: String? = null,
  val requiredPluginNames: Set<String> = emptySet(),
  val suggestToEnableRequiredPlugins: Boolean = false,
)
