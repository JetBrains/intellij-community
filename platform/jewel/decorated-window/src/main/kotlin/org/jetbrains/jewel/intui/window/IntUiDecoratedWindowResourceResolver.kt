package org.jetbrains.jewel.intui.window

import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.painter.ResourcePainterProvider
import org.jetbrains.jewel.window.styling.DecoratedWindowStyle

/** Creates a [ResourcePainterProvider] for loading a decorated window module resource. */
public fun decoratedWindowPainterProvider(path: String): ResourcePainterProvider =
    ResourcePainterProvider(path, DecoratedWindowStyle::class.java.classLoader, JewelTheme::class.java.classLoader)
