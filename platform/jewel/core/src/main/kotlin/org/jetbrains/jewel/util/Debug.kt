package org.jetbrains.jewel.util

import org.jetbrains.jewel.InternalJewelApi

/**
 * Determines whether we're in debug mode. This should not be used
 * in the bridge for logging; instead, you should use the IDE logger.
 */
@InternalJewelApi
val inDebugMode by lazy {
    System.getProperty("org.jetbrains.jewel.debug")?.toBoolean() ?: false
}
