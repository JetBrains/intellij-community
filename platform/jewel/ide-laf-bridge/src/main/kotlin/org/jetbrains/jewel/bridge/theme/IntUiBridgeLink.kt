package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.bridge.retrieveArcAsCornerSizeOrDefault
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.ui.component.styling.LinkColors
import org.jetbrains.jewel.ui.component.styling.LinkIcons
import org.jetbrains.jewel.ui.component.styling.LinkMetrics
import org.jetbrains.jewel.ui.component.styling.LinkStyle
import org.jetbrains.jewel.ui.component.styling.LinkUnderlineBehavior
import org.jetbrains.jewel.ui.icons.AllIconsKeys

internal fun readLinkStyle(): LinkStyle {
    val normalContent =
        retrieveColorOrUnspecified("Link.activeForeground").takeOrElse {
            retrieveColorOrUnspecified("Link.activeForeground")
        }

    val colors =
        LinkColors(
            content = normalContent,
            contentDisabled =
                retrieveColorOrUnspecified("Link.disabledForeground").takeOrElse {
                    retrieveColorOrUnspecified("Label.disabledForeground")
                },
            contentFocused = normalContent,
            contentPressed =
                retrieveColorOrUnspecified("Link.pressedForeground").takeOrElse {
                    retrieveColorOrUnspecified("link.pressed.foreground")
                },
            contentHovered =
                retrieveColorOrUnspecified("Link.hoverForeground").takeOrElse {
                    retrieveColorOrUnspecified("link.hover.foreground")
                },
            contentVisited =
                retrieveColorOrUnspecified("Link.visitedForeground").takeOrElse {
                    retrieveColorOrUnspecified("link.visited.foreground")
                },
        )

    return LinkStyle(
        colors = colors,
        metrics =
            LinkMetrics(
                focusHaloCornerSize =
                    retrieveArcAsCornerSizeOrDefault(
                        key = "ide.link.button.focus.round.arc",
                        default = CornerSize(4.dp),
                    ),
                textIconGap = 4.dp,
                iconSize = DpSize(16.dp, 16.dp),
            ),
        icons =
            LinkIcons(
                dropdownChevron = AllIconsKeys.General.ChevronDown,
                externalLink = AllIconsKeys.Ide.External_link_arrow,
            ),
        underlineBehavior = LinkUnderlineBehavior.ShowOnHover,
    )
}
