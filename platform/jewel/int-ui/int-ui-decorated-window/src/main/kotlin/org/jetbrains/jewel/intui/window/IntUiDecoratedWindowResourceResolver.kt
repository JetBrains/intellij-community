package org.jetbrains.jewel.intui.window

import org.jetbrains.jewel.intui.standalone.IntUiTheme
import org.jetbrains.jewel.intui.window.styling.IntUiDecoratedWindowStyle
import org.jetbrains.jewel.painter.ResourcePainterProvider

/**
 * Create [PainterProvider][org.jetbrains.jewel.painter.PainterProvider] for decorated window module resource.
 */
fun decoratedWindowPainterProvider(path: String) =
    ResourcePainterProvider(path, IntUiDecoratedWindowStyle::class.java.classLoader, IntUiTheme::class.java.classLoader)
