// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.util.text.HtmlChunk
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

/**
 * Represents a plugin loading error with both structured data and HTML message.
 */
@ApiStatus.Internal
class PluginLoadingError(
    val reason: PluginNonLoadReason?,
    private val htmlMessageSupplier: Supplier<HtmlChunk>,
    val error: Throwable?,
) {
  val htmlMessage: HtmlChunk
    get() = htmlMessageSupplier.get()
}