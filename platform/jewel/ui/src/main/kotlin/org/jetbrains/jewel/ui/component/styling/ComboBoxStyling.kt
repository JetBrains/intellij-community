package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.ComboBoxState
import org.jetbrains.jewel.ui.icon.IconKey

@Stable
@GenerateDataFunctions
public class ComboBoxStyle(
    public val colors: ComboBoxColors,
    public val metrics: ComboBoxMetrics,
    public val icons: ComboBoxIcons,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ComboBoxStyle

        if (colors != other.colors) return false
        if (metrics != other.metrics) return false
        if (icons != other.icons) return false

        return true
    }

    override fun hashCode(): Int {
        var result = colors.hashCode()
        result = 31 * result + metrics.hashCode()
        result = 31 * result + icons.hashCode()
        return result
    }

    override fun toString(): String = "ComboBoxStyle(colors=$colors, metrics=$metrics, icons=$icons)"

    public companion object
}

@Immutable
@GenerateDataFunctions
public class ComboBoxColors(
    public val background: Color,
    public val nonEditableBackground: Color,
    public val backgroundDisabled: Color,
    public val backgroundFocused: Color,
    public val backgroundPressed: Color,
    public val backgroundHovered: Color,
    public val content: Color,
    public val contentDisabled: Color,
    public val contentFocused: Color,
    public val contentPressed: Color,
    public val contentHovered: Color,
    public val border: Color,
    public val borderDisabled: Color,
    public val borderFocused: Color,
    public val borderPressed: Color,
    public val borderHovered: Color,
) {
    @Composable
    public fun backgroundFor(state: ComboBoxState, isEditable: Boolean): State<Color> =
        rememberUpdatedState(
            when {
                !isEditable && state.isEnabled -> nonEditableBackground
                !isEditable && !state.isEnabled -> backgroundDisabled
                state.isFocused && isEditable -> backgroundFocused
                state.isActive && isEditable -> background
                state.isPressed -> backgroundPressed
                state.isHovered -> backgroundHovered
                isEditable && !state.isEnabled -> backgroundDisabled
                isEditable -> background
                else -> background
            }
        )

    @Composable
    public fun contentFor(state: ComboBoxState): State<Color> =
        rememberUpdatedState(
            state.chooseValue(
                normal = content,
                disabled = contentDisabled,
                focused = contentFocused,
                pressed = contentPressed,
                hovered = contentHovered,
                active = content,
            )
        )

    @Composable
    public fun borderFor(state: ComboBoxState): State<Color> =
        rememberUpdatedState(
            state.chooseValue(
                normal = border,
                disabled = borderDisabled,
                focused = borderFocused,
                pressed = borderPressed,
                hovered = borderHovered,
                active = border,
            )
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ComboBoxColors

        if (background != other.background) return false
        if (nonEditableBackground != other.nonEditableBackground) return false
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

        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + nonEditableBackground.hashCode()
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
        return result
    }

    override fun toString(): String {
        return "ComboBoxColors(" +
            "background=$background, " +
            "nonEditableBackground=$nonEditableBackground, " +
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
            "borderHovered=$borderHovered" +
            ")"
    }

    public companion object
}

@Stable
@GenerateDataFunctions
public class ComboBoxMetrics(
    public val arrowAreaSize: DpSize,
    public val minSize: DpSize,
    public val cornerSize: CornerSize,
    public val contentPadding: PaddingValues,
    public val popupContentPadding: PaddingValues,
    public val borderWidth: Dp,
    public val maxPopupHeight: Dp,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ComboBoxMetrics

        if (arrowAreaSize != other.arrowAreaSize) return false
        if (minSize != other.minSize) return false
        if (cornerSize != other.cornerSize) return false
        if (contentPadding != other.contentPadding) return false
        if (popupContentPadding != other.popupContentPadding) return false
        if (borderWidth != other.borderWidth) return false
        if (maxPopupHeight != other.maxPopupHeight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = arrowAreaSize.hashCode()
        result = 31 * result + minSize.hashCode()
        result = 31 * result + cornerSize.hashCode()
        result = 31 * result + contentPadding.hashCode()
        result = 31 * result + popupContentPadding.hashCode()
        result = 31 * result + borderWidth.hashCode()
        result = 31 * result + maxPopupHeight.hashCode()
        return result
    }

    override fun toString(): String {
        return "ComboBoxMetrics(" +
            "arrowAreaSize=$arrowAreaSize, " +
            "minSize=$minSize, " +
            "cornerSize=$cornerSize, " +
            "contentPadding=$contentPadding, " +
            "popupContentPadding=$popupContentPadding, " +
            "borderWidth=$borderWidth, " +
            "maxPopupHeight=$maxPopupHeight" +
            ")"
    }

    public companion object
}

@Immutable
@GenerateDataFunctions
public class ComboBoxIcons(public val chevronDown: IconKey) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ComboBoxIcons

        return chevronDown == other.chevronDown
    }

    override fun hashCode(): Int = chevronDown.hashCode()

    override fun toString(): String = "ComboBoxIcons(chevronDown=$chevronDown)"

    public companion object
}

public val LocalDefaultComboBoxStyle: ProvidableCompositionLocal<ComboBoxStyle> = staticCompositionLocalOf {
    error("No DefaultComboBoxStyle provided. Have you forgotten the theme?")
}

public val LocalUndecoratedComboBoxStyle: ProvidableCompositionLocal<ComboBoxStyle> = staticCompositionLocalOf {
    error("No UndecoratedComboBoxStyle provided. Have you forgotten the theme?")
}
