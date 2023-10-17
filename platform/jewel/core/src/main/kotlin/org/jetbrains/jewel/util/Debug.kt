package org.jetbrains.jewel.util

import org.jetbrains.jewel.InternalJewelApi

@InternalJewelApi
val inDebugMode by lazy {
    System.getProperty("org.jetbrains.jewel.debug")?.toBoolean() ?: false
}
