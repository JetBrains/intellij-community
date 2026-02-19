package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.CheckboxColors
import org.jetbrains.jewel.ui.component.styling.CheckboxIcons
import org.jetbrains.jewel.ui.component.styling.CheckboxMetrics
import org.jetbrains.jewel.ui.component.styling.CheckboxStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.PathIconKey

public fun CheckboxStyle.Companion.light(
    colors: CheckboxColors = CheckboxColors.light(),
    metrics: CheckboxMetrics = CheckboxMetrics.defaults(),
    icons: CheckboxIcons = CheckboxIcons.light(),
): CheckboxStyle = CheckboxStyle(colors, metrics, icons)

public fun CheckboxStyle.Companion.dark(
    colors: CheckboxColors = CheckboxColors.dark(),
    metrics: CheckboxMetrics = CheckboxMetrics.defaults(),
    icons: CheckboxIcons = CheckboxIcons.dark(),
): CheckboxStyle = CheckboxStyle(colors, metrics, icons)

public fun CheckboxColors.Companion.light(
    content: Color = Color.Unspecified,
    contentDisabled: Color = IntUiLightTheme.colors.gray(8),
    contentSelected: Color = content,
): CheckboxColors = CheckboxColors(content, contentDisabled, contentSelected)

public fun CheckboxColors.Companion.dark(
    content: Color = Color.Unspecified,
    contentDisabled: Color = IntUiDarkTheme.colors.gray(7),
    contentSelected: Color = content,
): CheckboxColors = CheckboxColors(content, contentDisabled, contentSelected)

public fun CheckboxMetrics.Companion.defaults(
    checkboxSize: DpSize = DpSize(24.dp, 24.dp),
    outlineCornerSize: CornerSize = CornerSize(3.dp),
    outlineFocusedCornerSize: CornerSize = outlineCornerSize,
    outlineSelectedCornerSize: CornerSize = CornerSize(4.5.dp),
    outlineSelectedFocusedCornerSize: CornerSize = outlineSelectedCornerSize,
    outlineSize: DpSize = DpSize(16.dp, 16.dp),
    outlineFocusedSize: DpSize = outlineSize,
    outlineSelectedSize: DpSize = DpSize(20.dp, 20.dp),
    outlineSelectedFocusedSize: DpSize = outlineSelectedSize,
    iconContentGap: Dp = 5.dp,
): CheckboxMetrics =
    CheckboxMetrics(
        checkboxSize = checkboxSize,
        outlineCornerSize = outlineCornerSize,
        outlineFocusedCornerSize = outlineFocusedCornerSize,
        outlineSelectedCornerSize = outlineSelectedCornerSize,
        outlineSelectedFocusedCornerSize = outlineSelectedFocusedCornerSize,
        outlineSize = outlineSize,
        outlineFocusedSize = outlineFocusedSize,
        outlineSelectedSize = outlineSelectedSize,
        outlineSelectedFocusedSize = outlineSelectedFocusedSize,
        iconContentGap = iconContentGap,
    )

public fun CheckboxIcons.Companion.light(
    checkbox: IconKey =
        PathIconKey(path = "com/intellij/ide/ui/laf/icons/intellij/checkBox.svg", iconClass = CheckboxIcons::class.java)
): CheckboxIcons = CheckboxIcons(checkbox)

public fun CheckboxIcons.Companion.dark(
    checkbox: IconKey =
        PathIconKey(path = "com/intellij/ide/ui/laf/icons/darcula/checkBox.svg", iconClass = CheckboxIcons::class.java)
): CheckboxIcons = CheckboxIcons(checkbox)
