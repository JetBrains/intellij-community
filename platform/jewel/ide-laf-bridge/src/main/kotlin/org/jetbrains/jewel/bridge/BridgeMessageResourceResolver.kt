// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge

import com.intellij.ide.IdeBundle
import org.jetbrains.jewel.ui.util.MessageResourceResolver

/**
 * A provider for fetching localized strings using the IDE's message bundle.
 *
 * This class implements the [MessageResourceResolver] interface and delegates the retrieval of strings to the
 * [IdeBundle.message] method. It can be used to retrieve strings associated with a specific key from the IDE's resource
 * bundle.
 */
internal class BridgeMessageResourceResolver : MessageResourceResolver {
    /**
     * Fetches a string associated with the given key from the IDE's message bundle.
     *
     * This function uses the [IdeBundle.message] method to retrieve a localized string based on the provided key. The
     * key should correspond to a message ID defined in the IDE's resource bundle.
     *
     * @param key The key representing the string to fetch. This key typically corresponds to a message ID in the IDE's
     *   message bundle (e.g, ""action.text.copy.link.address").
     * @return The string associated with the provided key. If the key is not found, it will return an empty string.
     */
    override fun resolveIdeBundleMessage(key: String): String = IdeBundle.message(key)
}
