package org.jetbrains.jewel.intui.standalone

import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.painter.ResourcePainterProvider

/** Create a [PainterProvider][org.jetbrains.jewel.ui.painter.PainterProvider] to load a resource from the classpath. */
@Deprecated("Use IconKeys and the Icon composable instead")
public fun standalonePainterProvider(path: String): ResourcePainterProvider =
    ResourcePainterProvider(path, JewelTheme::class.java.classLoader)
