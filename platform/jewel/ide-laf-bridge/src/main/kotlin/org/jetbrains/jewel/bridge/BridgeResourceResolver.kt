package org.jetbrains.jewel.bridge

import com.intellij.util.ui.DirProvider
import org.jetbrains.jewel.ui.painter.ResourcePainterProvider

/**
 * [ResourcePainterProvider] to resolve resource in Intellij Module and
 * Bridge module.
 */
public fun bridgePainterProvider(path: String): ResourcePainterProvider =
    ResourcePainterProvider(path, DirProvider::class.java.classLoader, SwingBridgeService::class.java.classLoader)
