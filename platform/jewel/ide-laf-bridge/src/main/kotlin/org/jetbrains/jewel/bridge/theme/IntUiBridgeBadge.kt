package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.bridge.createVerticalBrush
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.retrieveColorsOrUnspecified
import org.jetbrains.jewel.ui.component.styling.BadgeColors
import org.jetbrains.jewel.ui.component.styling.BadgeMetrics
import org.jetbrains.jewel.ui.component.styling.BadgeStyle

// Note: there isn't a badge spec in the IntelliJ Platform LaF, so we're deriving
// this from similar small promotional UI elements.
// Badges are small colored elements used to display promotional text like "New", "Beta", etc.
internal fun readBadgeStyle(): BadgeStyle {
    val normalBackground =
        retrieveColorsOrUnspecified("Button.startBackground", "Button.endBackground").createVerticalBrush()

    val normalContent = retrieveColorOrUnspecified("Label.foreground")
    val disabledContent = retrieveColorOrUnspecified("Label.disabledForeground").takeOrElse { normalContent }

    val colors =
        BadgeColors(
            background = normalBackground,
            backgroundDisabled = normalBackground,
            backgroundFocused = normalBackground,
            backgroundPressed = normalBackground,
            backgroundHovered = normalBackground,
            content = normalContent,
            contentDisabled = disabledContent,
            contentFocused = normalContent,
            contentPressed = normalContent,
            contentHovered = normalContent,
        )

    return BadgeStyle(
        colors = colors,
        metrics =
            BadgeMetrics(
                cornerSize = CornerSize(0.dp),
                padding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                minSize = DpSize(32.dp, 18.dp),
            ),
    )
}
