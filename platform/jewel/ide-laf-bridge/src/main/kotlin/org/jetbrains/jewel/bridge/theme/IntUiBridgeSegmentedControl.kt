package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.SolidColor
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.createVerticalBrush
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.ui.component.styling.SegmentedControlColors
import org.jetbrains.jewel.ui.component.styling.SegmentedControlMetrics
import org.jetbrains.jewel.ui.component.styling.SegmentedControlStyle

internal fun readSegmentedControlStyle(): SegmentedControlStyle {
    val normalBorder =
        listOf(
                JBUI.CurrentTheme.Button.buttonOutlineColorStart(false).toComposeColor(),
                JBUI.CurrentTheme.Button.buttonOutlineColorEnd(false).toComposeColor(),
            )
            .createVerticalBrush()

    val colors =
        SegmentedControlColors(
            border = normalBorder,
            borderDisabled = SolidColor(JBUI.CurrentTheme.Button.disabledOutlineColor().toComposeColor()),
            borderPressed = normalBorder,
            borderHovered = normalBorder,
            borderFocused = SolidColor(JBUI.CurrentTheme.Button.focusBorderColor(false).toComposeColor()),
        )

    return SegmentedControlStyle(
        colors = colors,
        metrics =
            SegmentedControlMetrics(
                cornerSize = CornerSize(DarculaUIUtil.BUTTON_ARC.dp / 2),
                borderWidth = DarculaUIUtil.LW.dp,
            ),
    )
}
