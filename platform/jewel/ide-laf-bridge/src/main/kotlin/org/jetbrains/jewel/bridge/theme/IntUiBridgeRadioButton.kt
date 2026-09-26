package org.jetbrains.jewel.bridge.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import org.jetbrains.jewel.bridge.isDarculaTheme
import org.jetbrains.jewel.bridge.isNewUiTheme
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.retrieveIntAsNonNegativeDpOrUnspecified
import org.jetbrains.jewel.ui.component.styling.RadioButtonColors
import org.jetbrains.jewel.ui.component.styling.RadioButtonIcons
import org.jetbrains.jewel.ui.component.styling.RadioButtonMetrics
import org.jetbrains.jewel.ui.component.styling.RadioButtonStyle
import org.jetbrains.jewel.ui.icon.PathIconKey

internal fun readRadioButtonStyle(): RadioButtonStyle {
    val normalContent = retrieveColorOrUnspecified("RadioButton.foreground")
    val disabledContent = retrieveColorOrUnspecified("RadioButton.disabledText")
    val colors =
        RadioButtonColors(
            content = normalContent,
            contentHovered = normalContent,
            contentDisabled = disabledContent,
            contentSelected = normalContent,
            contentSelectedHovered = normalContent,
            contentSelectedDisabled = disabledContent,
        )

    val newUiTheme = isNewUiTheme()
    val metrics = if (newUiTheme && !isDarculaTheme()) NewUiRadioButtonMetrics else ClassicUiRadioButtonMetrics

    // This value is not normally defined in the themes, but Swing checks it anyway
    // The default hardcoded in
    // com.intellij.ide.ui.laf.darcula.ui.DarculaRadioButtonUI.getDefaultIcon()
    // is not correct though, the SVG is 19x19 and is missing 1px on the right
    val radioButtonSize =
        retrieveIntAsNonNegativeDpOrUnspecified("RadioButton.iconSize")
            .takeOrElse { metrics.radioButtonSize }
            .let { DpSize(it, it) }

    return RadioButtonStyle(
        colors = colors,
        metrics =
            RadioButtonMetrics(
                radioButtonSize = radioButtonSize,
                outlineSize = metrics.outlineSize,
                outlineFocusedSize = metrics.outlineFocusedSize,
                outlineSelectedSize = metrics.outlineSelectedSize,
                outlineSelectedFocusedSize = metrics.outlineSelectedFocusedSize,
                iconContentGap =
                    retrieveIntAsNonNegativeDpOrUnspecified("RadioButton.textIconGap").takeOrElse {
                        metrics.iconContentGap
                    },
            ),
        icons = RadioButtonIcons(radioButton = PathIconKey("${iconsBasePath}radio.svg", RadioButtonIcons::class.java)),
    )
}

private interface BridgeRadioButtonMetrics {
    val outlineSize: DpSize
    val outlineFocusedSize: DpSize
    val outlineSelectedSize: DpSize
    val outlineSelectedFocusedSize: DpSize

    val radioButtonSize: Dp
    val iconContentGap: Dp
}

private object ClassicUiRadioButtonMetrics : BridgeRadioButtonMetrics {
    override val outlineSize = DpSize(17.dp, 17.dp)
    override val outlineFocusedSize = DpSize(19.dp, 19.dp)
    override val outlineSelectedSize = outlineSize
    override val outlineSelectedFocusedSize = outlineFocusedSize

    override val radioButtonSize = 19.dp
    override val iconContentGap = 4.dp
}

private object NewUiRadioButtonMetrics : BridgeRadioButtonMetrics {
    override val outlineSize = DpSize(17.dp, 17.dp)
    override val outlineFocusedSize = outlineSize
    override val outlineSelectedSize = DpSize(22.dp, 22.dp)
    override val outlineSelectedFocusedSize = outlineSelectedSize

    override val radioButtonSize = 24.dp
    override val iconContentGap = 4.dp
}
