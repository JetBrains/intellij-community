package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import org.jetbrains.jewel.bridge.isDarculaTheme
import org.jetbrains.jewel.bridge.isNewUiTheme
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.retrieveIntAsNonNegativeDpOrUnspecified
import org.jetbrains.jewel.ui.component.styling.CheckboxColors
import org.jetbrains.jewel.ui.component.styling.CheckboxIcons
import org.jetbrains.jewel.ui.component.styling.CheckboxMetrics
import org.jetbrains.jewel.ui.component.styling.CheckboxStyle
import org.jetbrains.jewel.ui.icon.PathIconKey

internal fun readCheckboxStyle(): CheckboxStyle {
    val textColor = retrieveColorOrUnspecified("CheckBox.foreground")
    val colors =
        CheckboxColors(
            content = textColor,
            contentDisabled = retrieveColorOrUnspecified("CheckBox.disabledText"),
            contentSelected = textColor,
        )

    val newUiTheme = isNewUiTheme()
    val metrics = if (newUiTheme && !isDarculaTheme()) NewUiCheckboxMetrics else ClassicUiCheckboxMetrics

    // This value is not normally defined in the themes, but Swing checks it anyway.
    // The default hardcoded in
    // com.intellij.ide.ui.laf.darcula.ui.DarculaCheckBoxUI.getDefaultIcon()
    // is not correct though, the SVG is 19x19 and is missing 1px on the right
    val checkboxSize =
        retrieveIntAsNonNegativeDpOrUnspecified("CheckBox.iconSize").let {
            when {
                it.isSpecified -> DpSize(it, it)
                else -> metrics.checkboxSize
            }
        }

    return CheckboxStyle(
        colors = colors,
        metrics =
            CheckboxMetrics(
                checkboxSize = checkboxSize,
                outlineCornerSize = CornerSize(metrics.outlineCornerSize),
                outlineFocusedCornerSize = CornerSize(metrics.outlineFocusedCornerSize),
                outlineSelectedCornerSize = CornerSize(metrics.outlineSelectedCornerSize),
                outlineSelectedFocusedCornerSize = CornerSize(metrics.outlineSelectedFocusedCornerSize),
                outlineSize = metrics.outlineSize,
                outlineSelectedSize = metrics.outlineSelectedSize,
                outlineFocusedSize = metrics.outlineFocusedSize,
                outlineSelectedFocusedSize = metrics.outlineSelectedFocusedSize,
                iconContentGap = metrics.iconContentGap,
            ),
        icons = CheckboxIcons(checkbox = PathIconKey("${iconsBasePath}checkBox.svg", CheckboxIcons::class.java)),
    )
}

private interface BridgeCheckboxMetrics {
    val outlineSize: DpSize
    val outlineFocusedSize: DpSize
    val outlineSelectedSize: DpSize
    val outlineSelectedFocusedSize: DpSize

    val outlineCornerSize: Dp
    val outlineFocusedCornerSize: Dp
    val outlineSelectedCornerSize: Dp
    val outlineSelectedFocusedCornerSize: Dp

    val checkboxSize: DpSize
    val iconContentGap: Dp
}

private object ClassicUiCheckboxMetrics : BridgeCheckboxMetrics {
    override val outlineSize = DpSize(14.dp, 14.dp)
    override val outlineFocusedSize = DpSize(15.dp, 15.dp)
    override val outlineSelectedSize = outlineSize
    override val outlineSelectedFocusedSize = outlineFocusedSize

    override val outlineCornerSize = 2.dp
    override val outlineFocusedCornerSize = 3.dp
    override val outlineSelectedCornerSize = outlineCornerSize
    override val outlineSelectedFocusedCornerSize = outlineFocusedCornerSize

    override val checkboxSize = DpSize(20.dp, 19.dp)
    override val iconContentGap = 4.dp
}

private object NewUiCheckboxMetrics : BridgeCheckboxMetrics {
    override val outlineSize = DpSize(16.dp, 16.dp)
    override val outlineFocusedSize = outlineSize
    override val outlineSelectedSize = DpSize(20.dp, 20.dp)
    override val outlineSelectedFocusedSize = outlineSelectedSize

    override val outlineCornerSize = 3.dp
    override val outlineFocusedCornerSize = outlineCornerSize
    override val outlineSelectedCornerSize = 4.5.dp
    override val outlineSelectedFocusedCornerSize = outlineSelectedCornerSize

    override val checkboxSize = DpSize(24.dp, 24.dp)
    override val iconContentGap = 5.dp
}
