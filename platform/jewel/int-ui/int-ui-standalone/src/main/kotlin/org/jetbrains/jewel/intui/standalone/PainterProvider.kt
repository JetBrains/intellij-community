package org.jetbrains.jewel.intui.standalone

import org.jetbrains.jewel.JewelTheme
import org.jetbrains.jewel.painter.ResourcePainterProvider

/**
 * Create a [PainterProvider][org.jetbrains.jewel.painter.PainterProvider] to load a
 * resource from the classpath.
 */
fun standalonePainterProvider(path: String) =
    ResourcePainterProvider(path, JewelTheme::class.java.classLoader)
