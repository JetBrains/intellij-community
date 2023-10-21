package org.jetbrains.jewel.intui.window

import org.jetbrains.jewel.JewelTheme
import org.jetbrains.jewel.painter.ResourcePainterProvider
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle

/**
 * Create [PainterProvider][org.jetbrains.jewel.painter.PainterProvider] for decorated window module resource.
 */
fun decoratedWindowPainterProvider(path: String) =
    ResourcePainterProvider(path, DecoratedWindowStyle::class.java.classLoader, JewelTheme::class.java.classLoader)
