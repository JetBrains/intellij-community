package org.jetbrains.jewel.bridge

import com.intellij.util.ui.DirProvider
import org.jetbrains.jewel.painter.ResourcePainterProvider

/**
 * [ResourceResolver] to resolve resource in Intellij Module and Bridge module.
 */
fun bridgePainterProvider(path: String) =
    ResourcePainterProvider(path, DirProvider::class.java.classLoader, SwingBridgeService::class.java.classLoader)
