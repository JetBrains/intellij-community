package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@Stable
@GenerateDataFunctions
public class TextFieldStyle(
    override val colors: TextFieldColors,
    override val metrics: TextFieldMetrics,
    public val iconButtonStyle: IconButtonStyle,
) : InputFieldStyle {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TextFieldStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (iconButtonStyle != other.iconButtonStyle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + iconButtonStyle.hashCode()
        return result
    }

    override fun toString(): String =
        "TextFieldStyle(colors=$colors, metrics=$metrics, iconButtonStyle=$iconButtonStyle)"

    public companion object
}

@Immutable
@GenerateDataFunctions
public class TextFieldColors(
    override val background: Color,
    override val backgroundDisabled: Color,
    override val backgroundFocused: Color,
    override val backgroundPressed: Color,
    override val backgroundHovered: Color,
    override val content: Color,
    override val contentDisabled: Color,
    override val contentFocused: Color,
    override val contentPressed: Color,
    override val contentHovered: Color,
    override val border: Color,
    override val borderDisabled: Color,
    override val borderFocused: Color,
    override val borderPressed: Color,
    override val borderHovered: Color,
    override val caret: Color,
    override val caretDisabled: Color,
    override val caretFocused: Color,
    override val caretPressed: Color,
    override val caretHovered: Color,
    public val placeholder: Color,
) : InputFieldColors {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TextFieldColors

        if (background != other.background) return false
        if (backgroundDisabled != other.backgroundDisabled) return false
        if (backgroundFocused != other.backgroundFocused) return false
        if (backgroundPressed != other.backgroundPressed) return false
        if (backgroundHovered != other.backgroundHovered) return false
        if (content != other.content) return false
        if (contentDisabled != other.contentDisabled) return false
        if (contentFocused != other.contentFocused) return false
        if (contentPressed != other.contentPressed) return false
        if (contentHovered != other.contentHovered) return false
        if (border != other.border) return false
        if (borderDisabled != other.borderDisabled) return false
        if (borderFocused != other.borderFocused) return false
        if (borderPressed != other.borderPressed) return false
        if (borderHovered != other.borderHovered) return false
        if (caret != other.caret) return false
        if (caretDisabled != other.caretDisabled) return false
        if (caretFocused != other.caretFocused) return false
        if (caretPressed != other.caretPressed) return false
        if (caretHovered != other.caretHovered) return false
        if (placeholder != other.placeholder) return false

        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + backgroundDisabled.hashCode()
        result = 31 * result + backgroundFocused.hashCode()
        result = 31 * result + backgroundPressed.hashCode()
        result = 31 * result + backgroundHovered.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + contentDisabled.hashCode()
        result = 31 * result + contentFocused.hashCode()
        result = 31 * result + contentPressed.hashCode()
        result = 31 * result + contentHovered.hashCode()
        result = 31 * result + border.hashCode()
        result = 31 * result + borderDisabled.hashCode()
        result = 31 * result + borderFocused.hashCode()
        result = 31 * result + borderPressed.hashCode()
        result = 31 * result + borderHovered.hashCode()
        result = 31 * result + caret.hashCode()
        result = 31 * result + caretDisabled.hashCode()
        result = 31 * result + caretFocused.hashCode()
        result = 31 * result + caretPressed.hashCode()
        result = 31 * result + caretHovered.hashCode()
        result = 31 * result + placeholder.hashCode()
        return result
    }

    override fun toString(): String {
        return "TextFieldColors(" +
            "background=$background, " +
            "backgroundDisabled=$backgroundDisabled, " +
            "backgroundFocused=$backgroundFocused, " +
            "backgroundPressed=$backgroundPressed, " +
            "backgroundHovered=$backgroundHovered, " +
            "content=$content, " +
            "contentDisabled=$contentDisabled, " +
            "contentFocused=$contentFocused, " +
            "contentPressed=$contentPressed, " +
            "contentHovered=$contentHovered, " +
            "border=$border, " +
            "borderDisabled=$borderDisabled, " +
            "borderFocused=$borderFocused, " +
            "borderPressed=$borderPressed, " +
            "borderHovered=$borderHovered, " +
            "caret=$caret, " +
            "caretDisabled=$caretDisabled, " +
            "caretFocused=$caretFocused, " +
            "caretPressed=$caretPressed, " +
            "caretHovered=$caretHovered, " +
            "placeholder=$placeholder" +
            ")"
    }

    public companion object
}

@Stable
@GenerateDataFunctions
public class TextFieldMetrics(
    override val borderWidth: Dp,
    override val contentPadding: PaddingValues,
    override val cornerSize: CornerSize,
    override val minSize: DpSize,
) : InputFieldMetrics {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TextFieldMetrics

        if (borderWidth != other.borderWidth) return false
        if (contentPadding != other.contentPadding) return false
        if (cornerSize != other.cornerSize) return false
        if (minSize != other.minSize) return false

        return true
    }

    override fun hashCode(): Int {
        var result = borderWidth.hashCode()
        result = 31 * result + contentPadding.hashCode()
        result = 31 * result + cornerSize.hashCode()
        result = 31 * result + minSize.hashCode()
        return result
    }

    override fun toString(): String {
        return "TextFieldMetrics(" +
            "borderWidth=$borderWidth, " +
            "contentPadding=$contentPadding, " +
            "cornerSize=$cornerSize, " +
            "minSize=$minSize" +
            ")"
    }

    public companion object
}

public val LocalTextFieldStyle: ProvidableCompositionLocal<TextFieldStyle> = staticCompositionLocalOf {
    error("No TextFieldStyle provided. Have you forgotten the theme?")
}
