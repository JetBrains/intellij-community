package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.shape.CornerSize
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ui.JBUI
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.bridge.toPaddingValues
import org.jetbrains.jewel.ui.component.styling.TooltipColors
import org.jetbrains.jewel.ui.component.styling.TooltipMetrics
import org.jetbrains.jewel.ui.component.styling.TooltipStyle

internal fun readTooltipStyle(): TooltipStyle {
    return TooltipStyle(
        metrics =
            TooltipMetrics.defaults(
                contentPadding = JBUI.CurrentTheme.HelpTooltip.smallTextBorderInsets().toPaddingValues(),
                showDelay = Registry.intValue("ide.tooltip.initialReshowDelay").milliseconds,
                cornerSize = CornerSize(JBUI.CurrentTheme.Tooltip.CORNER_RADIUS.dp),
            ),
        colors =
            TooltipColors(
                content = retrieveColorOrUnspecified("ToolTip.foreground"),
                background = retrieveColorOrUnspecified("ToolTip.background"),
                border = JBUI.CurrentTheme.Tooltip.borderColor().toComposeColor(),
                shadow = retrieveColorOrUnspecified("Notification.Shadow.bottom1Color"),
            ),
    )
}
