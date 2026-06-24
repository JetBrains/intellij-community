// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.syntax

import com.intellij.platform.syntax.extensions.ExtensionPointKey
import com.intellij.platform.syntax.extensions.currentExtensionSupport
import org.jetbrains.annotations.ApiStatus

/**
 * Implement this extension point to disable ("veto") lazy parsing of JSON.
 *
 * Lazy parsing is enabled by default and stays enabled unless some vetoer vetoes it. This is how the syntax lib,
 * which has no dependency on the IntelliJ platform, learns about the IntelliJ-side `json.lazy.parsing` registry flag.
 */
@ApiStatus.Internal
@ApiStatus.OverrideOnly
interface JsonLazyParsingVetoer {
  fun isLazyParsingVetoed(): Boolean
}

private val jsonLazyParsingVetoerEP: ExtensionPointKey<JsonLazyParsingVetoer> =
  ExtensionPointKey("com.intellij.json.lazyParsingVetoer")

/**
 * Handles the lazy parsing switch of JSON files in the syntax lib which has no dependency on the IntelliJ platform.
 *
 * Lazy parsing is enabled unless some [JsonLazyParsingVetoer] vetoes it. The query is defensive — if no extension
 * support / extension point is available (very early startup, or a lightweight test fixture that did not register the
 * extension point), lazy parsing stays enabled, matching the registry default.
 *
 * @see JsonLazyParsingVetoer
 */
val JsonLazyParsing: Boolean
  get() = try {
    currentExtensionSupport().getExtensions(jsonLazyParsingVetoerEP).none { it.isLazyParsingVetoed() }
  }
  catch (_: Throwable) {
    true
  }
