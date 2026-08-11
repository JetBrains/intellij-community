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

/** Combines [ComboBoxColors], [ComboBoxMetrics], and [ComboBoxIcons] to fully style a ComboBox component. */
@Stable
@GenerateDataFunctions
public class ComboBoxStyle(
    /** The color tokens for the combo box. */
    public val colors: ComboBoxColors,
    /** The size and spacing metrics for the combo box. */
    public val metrics: ComboBoxMetrics,
    /** The icon keys for the combo box. */
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

    /** Companion object for [ComboBoxStyle]. */
    public companion object
}

/**
 * Holds color tokens for the ComboBox component in its various states (normal, disabled, focused, pressed, hovered).
 */
@Immutable
@GenerateDataFunctions
public class ComboBoxColors(
    /** The background color in the normal state. */
    public val background: Color,
    /** The background color when the combo box is not editable. */
    public val nonEditableBackground: Color,
    /** The background color in the disabled state. */
    public val backgroundDisabled: Color,
    /** The background color in the focused state. */
    public val backgroundFocused: Color,
    /** The background color in the pressed state. */
    public val backgroundPressed: Color,
    /** The background color in the hovered state. */
    public val backgroundHovered: Color,
    /** The content (text/icon) color in the normal state. */
    public val content: Color,
    /** The content color in the disabled state. */
    public val contentDisabled: Color,
    /** The content color in the focused state. */
    public val contentFocused: Color,
    /** The content color in the pressed state. */
    public val contentPressed: Color,
    /** The content color in the hovered state. */
    public val contentHovered: Color,
    /** The border color in the normal state. */
    public val border: Color,
    /** The border color in the disabled state. */
    public val borderDisabled: Color,
    /** The border color in the focused state. */
    public val borderFocused: Color,
    /** The border color in the pressed state. */
    public val borderPressed: Color,
    /** The border color in the hovered state. */
    public val borderHovered: Color,
) {
    /**
     * Returns a [State] holding the background color appropriate for the given [state], taking editability into
     * account.
     *
     * @param state the current interaction state of the combo box.
     * @param isEditable whether the combo box allows text input.
     */
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

    /** Returns a [State] holding the content (text/icon) color appropriate for the given [state]. */
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

    /** Returns a [State] holding the border color appropriate for the given [state]. */
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

    /** Companion object for [ComboBoxColors]. */
    public companion object
}

/**
 * Holds size and spacing metrics for the ComboBox component, including arrow area, corner, padding, border, and popup
 * dimensions.
 */
@Stable
@GenerateDataFunctions
public class ComboBoxMetrics(
    /** The size of the arrow (chevron) area on the trailing side. */
    public val arrowAreaSize: DpSize,
    /** The minimum size of the combo box. */
    public val minSize: DpSize,
    /** The corner radius of the combo box. */
    public val cornerSize: CornerSize,
    /** The padding applied to the combo box content. */
    public val contentPadding: PaddingValues,
    /** The padding applied to the popup content. */
    public val popupContentPadding: PaddingValues,
    /** The width of the combo box border. */
    public val borderWidth: Dp,
    /** The maximum height of the popup dropdown. */
    public val maxPopupHeight: Dp,
    /** The maximum number of visible rows in the popup dropdown. */
    public val maxPopupRowCount: Int,
) {
    init {
        require(maxPopupRowCount >= 1) { "maxPopupRowCount must be at least 1, was $maxPopupRowCount" }
    }

    public constructor(
        arrowAreaSize: DpSize,
        minSize: DpSize,
        cornerSize: CornerSize,
        contentPadding: PaddingValues,
        popupContentPadding: PaddingValues,
        borderWidth: Dp,
        maxPopupHeight: Dp,
    ) : this(arrowAreaSize, minSize, cornerSize, contentPadding, popupContentPadding, borderWidth, maxPopupHeight, 10)

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
        if (maxPopupRowCount != other.maxPopupRowCount) return false

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
        result = 31 * result + maxPopupRowCount.hashCode()
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
            "maxPopupRowCount=$maxPopupRowCount" +
            ")"
    }

    /** Companion object for [ComboBoxMetrics]. */
    public companion object
}

/** Holds icon keys for the ComboBox component, providing the chevron-down arrow icon. */
@Immutable
@GenerateDataFunctions
public class ComboBoxIcons(
    /** The icon key for the chevron-down arrow. */
    public val chevronDown: IconKey
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ComboBoxIcons

        return chevronDown == other.chevronDown
    }

    override fun hashCode(): Int = chevronDown.hashCode()

    override fun toString(): String = "ComboBoxIcons(chevronDown=$chevronDown)"

    /** Companion object for [ComboBoxIcons]. */
    public companion object
}

/** CompositionLocal providing the default (bordered) [ComboBoxStyle] for the current theme. */
public val LocalDefaultComboBoxStyle: ProvidableCompositionLocal<ComboBoxStyle> = staticCompositionLocalOf {
    error("No DefaultComboBoxStyle provided. Have you forgotten the theme?")
}

/** CompositionLocal providing the undecorated (borderless) [ComboBoxStyle] for the current theme. */
public val LocalUndecoratedComboBoxStyle: ProvidableCompositionLocal<ComboBoxStyle> = staticCompositionLocalOf {
    error("No UndecoratedComboBoxStyle provided. Have you forgotten the theme?")
}
