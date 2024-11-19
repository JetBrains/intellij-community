package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.retrieveInsetsAsPaddingValues
import org.jetbrains.jewel.bridge.toDpSize
import org.jetbrains.jewel.ui.component.styling.ComboBoxColors
import org.jetbrains.jewel.ui.component.styling.ComboBoxIcons
import org.jetbrains.jewel.ui.component.styling.ComboBoxMetrics
import org.jetbrains.jewel.ui.component.styling.ComboBoxStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys

internal fun readDefaultComboBoxStyle(): ComboBoxStyle {
    val normalBackground = retrieveColorOrUnspecified("ComboBox.background")
    val nonEditableBackground = retrieveColorOrUnspecified("ComboBox.nonEditableBackground")
    val normalContent = retrieveColorOrUnspecified("ComboBox.foreground")
    val normalBorder = retrieveColorOrUnspecified("Component.borderColor")
    val focusedBorder = retrieveColorOrUnspecified("Component.focusedBorderColor")

    val colors =
        ComboBoxColors(
            background = normalBackground,
            nonEditableBackground = nonEditableBackground,
            backgroundDisabled = retrieveColorOrUnspecified("ComboBox.disabledBackground"),
            backgroundFocused = normalBackground,
            backgroundPressed = normalBackground,
            backgroundHovered = normalBackground,
            content = normalContent,
            contentDisabled = retrieveColorOrUnspecified("ComboBox.disabledForeground"),
            contentFocused = normalContent,
            contentPressed = normalContent,
            contentHovered = normalContent,
            border = normalBorder,
            borderDisabled = retrieveColorOrUnspecified("Component.disabledBorderColor"),
            borderFocused = focusedBorder,
            borderPressed = focusedBorder,
            borderHovered = normalBorder,
        )

    val minimumSize = JBUI.CurrentTheme.ComboBox.minimumSize().toDpSize()
    val arrowWidth = JBUI.CurrentTheme.Component.ARROW_AREA_WIDTH.dp
    return ComboBoxStyle(
        colors = colors,
        metrics =
            ComboBoxMetrics(
                arrowAreaSize = DpSize(arrowWidth, minimumSize.height),
                minSize = DpSize(minimumSize.width + arrowWidth, minimumSize.height),
                cornerSize = componentArc,
                contentPadding = retrieveInsetsAsPaddingValues("ComboBox.padding"),
                popupContentPadding = PaddingValues(6.dp),
                borderWidth = DarculaUIUtil.LW.dp,
                maxPopupHeight = Dp.Unspecified,
            ),
        icons = ComboBoxIcons(chevronDown = AllIconsKeys.General.ChevronDown),
    )
}
