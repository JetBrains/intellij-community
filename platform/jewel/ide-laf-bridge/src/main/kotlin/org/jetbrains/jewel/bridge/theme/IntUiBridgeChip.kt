package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.bridge.createVerticalBrush
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.retrieveColorsOrUnspecified
import org.jetbrains.jewel.ui.component.styling.ChipColors
import org.jetbrains.jewel.ui.component.styling.ChipMetrics
import org.jetbrains.jewel.ui.component.styling.ChipStyle

// Note: there isn't a chip spec, nor a chip UI, so we're deriving this from the
// styling defined in com.intellij.ide.ui.experimental.meetNewUi.MeetNewUiButton
// To note:
//  1. There is no real disabled state, we're making it sort of up
//  2. Chips can be used as buttons (see run configs) or as radio buttons (see MeetNewUi)
//  3. We also have a toggleable version because why not
internal fun readChipStyle(): ChipStyle {
    val normalBackground =
        retrieveColorsOrUnspecified("Button.startBackground", "Button.endBackground").createVerticalBrush()
    val normalContent = retrieveColorOrUnspecified("Label.foreground")
    val normalBorder = retrieveColorOrUnspecified("Button.startBorderColor")
    val disabledBorder = retrieveColorOrUnspecified("Button.disabledBorderColor")
    val selectedBorder = retrieveColorOrUnspecified("Component.focusColor")

    val colors =
        ChipColors(
            background = normalBackground,
            backgroundDisabled = normalBackground,
            backgroundFocused = normalBackground,
            backgroundPressed = normalBackground,
            backgroundHovered = normalBackground,
            backgroundSelected = normalBackground,
            backgroundSelectedDisabled = normalBackground,
            backgroundSelectedPressed = normalBackground,
            backgroundSelectedFocused = normalBackground,
            backgroundSelectedHovered = normalBackground,
            content = normalContent,
            contentDisabled = normalContent,
            contentFocused = normalContent,
            contentPressed = normalContent,
            contentHovered = normalContent,
            contentSelected = normalContent,
            contentSelectedDisabled = normalContent,
            contentSelectedPressed = normalContent,
            contentSelectedFocused = normalContent,
            contentSelectedHovered = normalContent,
            border = normalBorder,
            borderDisabled = disabledBorder,
            borderFocused = normalBorder,
            borderPressed = normalBorder,
            borderHovered = normalBorder,
            borderSelected = selectedBorder,
            borderSelectedDisabled = disabledBorder,
            borderSelectedPressed = selectedBorder,
            borderSelectedFocused = selectedBorder,
            borderSelectedHovered = selectedBorder,
        )

    return ChipStyle(
        colors = colors,
        metrics =
            ChipMetrics(
                cornerSize = CornerSize(6.dp),
                padding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                borderWidth = 1.dp,
                borderWidthSelected = 2.dp,
            ),
    )
}
