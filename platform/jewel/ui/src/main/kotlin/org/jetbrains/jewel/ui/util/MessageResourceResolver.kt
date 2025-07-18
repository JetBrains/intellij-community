// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.util

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi

/**
 * Interface for resolving messages from the IDE's resource bundle.
 *
 * This interface provides a mechanism to resolve strings based on a given key. It is used to fetch UI strings from the
 * IDE's internal resource bundle.
 */
@ApiStatus.Internal
@InternalJewelApi
public interface MessageResourceResolver {
    /**
     * Resolves a message using the provided key.
     *
     * @param key The key representing the message to resolve.
     * @return The resolved message string associated with the provided key. If the key is not found, an empty string
     *   will be returned.
     */
    public fun resolveIdeBundleMessage(key: String): String
}

public val LocalMessageResourceResolverProvider: ProvidableCompositionLocal<MessageResourceResolver> =
    staticCompositionLocalOf {
        error("No LocalMessageResourceResolverProvider provided. Have you forgotten the theme?")
    }
