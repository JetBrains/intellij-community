package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.ui.component.styling.TextAreaColors
import org.jetbrains.jewel.ui.component.styling.TextAreaMetrics
import org.jetbrains.jewel.ui.component.styling.TextAreaStyle
import org.jetbrains.jewel.ui.component.styling.TextFieldMetrics

internal fun readTextAreaStyle(metrics: TextFieldMetrics): TextAreaStyle {
    val normalBackground = retrieveColorOrUnspecified("TextArea.background")
    val normalContent = retrieveColorOrUnspecified("TextArea.foreground")
    val normalBorder = DarculaUIUtil.getOutlineColor(true, false).toComposeColor()
    val focusedBorder = DarculaUIUtil.getOutlineColor(true, true).toComposeColor()
    val normalCaret = retrieveColorOrUnspecified("TextArea.caretForeground")

    val colors =
        TextAreaColors(
            background = normalBackground,
            backgroundDisabled = Color.Unspecified,
            backgroundFocused = normalBackground,
            backgroundPressed = normalBackground,
            backgroundHovered = normalBackground,
            content = normalContent,
            contentDisabled = retrieveColorOrUnspecified("TextArea.inactiveForeground"),
            contentFocused = normalContent,
            contentPressed = normalContent,
            contentHovered = normalContent,
            border = normalBorder,
            borderDisabled = DarculaUIUtil.getOutlineColor(false, false).toComposeColor(),
            borderFocused = focusedBorder,
            borderPressed = focusedBorder,
            borderHovered = normalBorder,
            caret = normalCaret,
            caretDisabled = normalCaret,
            caretFocused = normalCaret,
            caretPressed = normalCaret,
            caretHovered = normalCaret,
            placeholder = NamedColorUtil.getInactiveTextColor().toComposeColor(),
        )

    return TextAreaStyle(
        colors = colors,
        metrics =
            TextAreaMetrics(
                cornerSize = metrics.cornerSize,
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp),
                minSize = metrics.minSize,
                borderWidth = metrics.borderWidth,
            ),
    )
}
