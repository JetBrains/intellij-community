// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge

import androidx.compose.ui.platform.UriHandler
import com.intellij.ide.BrowserUtil

/**
 * A custom implementation of [UriHandler] that delegates the handling to IntelliJ's BrowserUtil class.
 *
 * This object overrides the [openUri] function to invoke the [BrowserUtil.browse] method, which attempts to open the
 * provided URI. If the URI is valid, it opens in the system's default web browser. If the URI is invalid (e.g., an
 * empty string), it may open a local file explorer (Windows) or Finder (macOS), depending on the URI and operating
 * system.
 *
 * Example usage:
 * ```
 * BridgeUriHandler.openUri("https://www.example.com")
 * BridgeUriHandler.openUri("") // May open file explorer or Finder
 * ```
 */
public object BridgeUriHandler : UriHandler {
    override fun openUri(uri: String) {
        BrowserUtil.browse(uri)
    }
}
