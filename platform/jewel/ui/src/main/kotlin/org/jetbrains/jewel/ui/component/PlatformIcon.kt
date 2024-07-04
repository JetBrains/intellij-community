package org.jetbrains.jewel.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.ui.icon.IntelliJIconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.PainterHint

@Composable
public fun PlatformIcon(
    key: IntelliJIconKey,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    hint: PainterHint,
) {
    PlatformIcon(key, contentDescription, modifier, tint, *arrayOf(hint))
}

@Composable
public fun PlatformIcon(
    key: IntelliJIconKey,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    vararg hints: PainterHint,
) {
    Icon(key, contentDescription, AllIconsKeys::class.java, modifier, tint, *hints)
}
