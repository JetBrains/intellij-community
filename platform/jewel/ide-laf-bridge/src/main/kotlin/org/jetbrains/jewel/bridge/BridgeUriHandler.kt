// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge

import androidx.compose.ui.platform.UriHandler
import com.intellij.ide.BrowserUtil

public class BridgeUriHandler : UriHandler {
    override fun openUri(uri: String) {
        BrowserUtil.browse(uri)
    }
}
