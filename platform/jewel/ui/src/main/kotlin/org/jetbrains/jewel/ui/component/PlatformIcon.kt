package org.jetbrains.jewel.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.jewel.ui.icon.IntelliJIconKey
import org.jetbrains.jewel.ui.painter.PainterHint

@Deprecated(
    "Use Icon directly, this doesn't have any advantage over it anymore.",
    ReplaceWith("Icon(key, contentDescription, modifier, tint, hint)", "com.jewel.ui.component.Icon"),
)
@ScheduledForRemoval(inVersion = "Before 1.0")
@Composable
public fun PlatformIcon(
    key: IntelliJIconKey,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    hint: PainterHint,
) {
    @Suppress("DEPRECATION") // Everything is deprecated here anyway
    PlatformIcon(key, contentDescription, modifier, tint, *arrayOf(hint))
}

@Deprecated(
    "Use Icon directly, this doesn't have any advantage over it anymore.",
    ReplaceWith("Icon(key, contentDescription, modifier, tint, hints)", "com.jewel.ui.component.Icon"),
)
@ScheduledForRemoval(inVersion = "Before 1.0")
@Composable
public fun PlatformIcon(
    key: IntelliJIconKey,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    vararg hints: PainterHint,
) {
    Icon(key, contentDescription, modifier, key::class.java, tint, *hints)
}
