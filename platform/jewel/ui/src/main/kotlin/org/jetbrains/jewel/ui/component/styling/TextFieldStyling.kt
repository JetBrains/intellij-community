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

/** Combines colors, metrics, and an [IconButtonStyle] for styling a text field component. */
@Stable
@GenerateDataFunctions
public class TextFieldStyle(
    /** The color tokens for the text field. */
    override val colors: TextFieldColors,
    /** The size and spacing metrics for the text field. */
    override val metrics: TextFieldMetrics,
    /**
     * The [IconButtonStyle] to use for icon buttons that callers place in the text field's leading or trailing icon
     * slots. Not applied automatically.
     */
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

    /** Companion object for [TextFieldStyle]. */
    public companion object
}

/** Holds color tokens for the text field component in its various interaction and focus states. */
@Immutable
@GenerateDataFunctions
public class TextFieldColors(
    /** The background color in the default state. */
    override val background: Color,
    /** The background color in the disabled state. */
    override val backgroundDisabled: Color,
    /** The background color in the focused state. */
    override val backgroundFocused: Color,
    /** The background color in the pressed state. */
    override val backgroundPressed: Color,
    /** The background color in the hovered state. */
    override val backgroundHovered: Color,
    /** The text content color in the default state. */
    override val content: Color,
    /** The text content color in the disabled state. */
    override val contentDisabled: Color,
    /** The text content color in the focused state. */
    override val contentFocused: Color,
    /** The text content color in the pressed state. */
    override val contentPressed: Color,
    /** The text content color in the hovered state. */
    override val contentHovered: Color,
    /** The border color in the default state. */
    override val border: Color,
    /** The border color in the disabled state. */
    override val borderDisabled: Color,
    /** The border color in the focused state. */
    override val borderFocused: Color,
    /** The border color in the pressed state. */
    override val borderPressed: Color,
    /** The border color in the hovered state. */
    override val borderHovered: Color,
    /** The caret color in the default state. */
    override val caret: Color,
    /** The caret color in the disabled state. */
    override val caretDisabled: Color,
    /** The caret color in the focused state. */
    override val caretFocused: Color,
    /** The caret color in the pressed state. */
    override val caretPressed: Color,
    /** The caret color in the hovered state. */
    override val caretHovered: Color,
    /** The color of the placeholder text shown when the field is empty. */
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

    /** Companion object for [TextFieldColors]. */
    public companion object
}

/**
 * Holds size and spacing metrics for the text field component, including border width, padding, corner size, and
 * minimum size.
 */
@Stable
@GenerateDataFunctions
public class TextFieldMetrics(
    /** The width of the border stroke. */
    override val borderWidth: Dp,
    /** The padding applied around the text content inside the field. */
    override val contentPadding: PaddingValues,
    /** The corner radius of the text field. */
    override val cornerSize: CornerSize,
    /** The minimum size of the text field. */
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

    /** Companion object for [TextFieldMetrics]. */
    public companion object
}

/** CompositionLocal used to provide the current [TextFieldStyle] down the composition tree. */
public val LocalTextFieldStyle: ProvidableCompositionLocal<TextFieldStyle> = staticCompositionLocalOf {
    error("No TextFieldStyle provided. Have you forgotten the theme?")
}
