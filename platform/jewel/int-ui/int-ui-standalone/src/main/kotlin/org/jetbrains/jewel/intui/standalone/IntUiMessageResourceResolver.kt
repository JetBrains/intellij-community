// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone

import org.jetbrains.jewel.ui.util.MessageResourceResolver

/**
 * A provider for UI strings that returns localized or default string values based on a given key.
 *
 * This class implements the [MessageResourceResolver] interface and is responsible for fetching string values
 * associated with specific keys. It can be used to retrieve action text for UI elements, like buttons or menu items
 * based on the string keys from IntelliJ Platform resource bundle.
 */
internal class IntUiMessageResourceResolver : MessageResourceResolver {
    /**
     * Fetches the string associated with a given key.
     *
     * This function looks up the provided key and returns the corresponding string value. If the key is not found, it
     * returns an empty string by default.
     *
     * @param key The key representing the string to fetch. This key typically corresponds to a message ID in the IDE's
     *   message bundle (e.g, ""action.text.copy.link.address").
     * @return The string associated with the provided key. If the key is not found, an empty string is returned.
     */
    override fun resolveIdeBundleMessage(key: String): String =
        when (key) {
            "action.text.open.link.in.browser" -> "Open Link in Browser"
            "action.text.copy.link.address" -> "Copy Link Address"
            "action.text.more" -> "More"
            else -> ""
        }
}
