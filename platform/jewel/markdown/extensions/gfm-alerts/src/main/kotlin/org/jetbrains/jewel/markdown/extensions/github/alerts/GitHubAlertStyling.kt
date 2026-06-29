package org.jetbrains.jewel.markdown.extensions.github.alerts

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.icon.IconKey

/**
 * Styling for the different types of GFM alerts.
 *
 * @param note Styling for the "note" alert.
 * @param tip Styling for the "tip" alert.
 * @param important Styling for the "important" alert.
 * @param warning Styling for the "warning" alert.
 * @param caution Styling for the "caution" alert.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class AlertStyling(
    /** Styling for the "note" alert. */
    public val note: NoteAlertStyling,
    /** Styling for the "tip" alert. */
    public val tip: TipAlertStyling,
    /** Styling for the "important" alert. */
    public val important: ImportantAlertStyling,
    /** Styling for the "warning" alert. */
    public val warning: WarningAlertStyling,
    /** Styling for the "caution" alert. */
    public val caution: CautionAlertStyling,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AlertStyling

        if (note != other.note) return false
        if (tip != other.tip) return false
        if (important != other.important) return false
        if (warning != other.warning) return false
        if (caution != other.caution) return false

        return true
    }

    override fun hashCode(): Int {
        var result = note.hashCode()
        result = 31 * result + tip.hashCode()
        result = 31 * result + important.hashCode()
        result = 31 * result + warning.hashCode()
        result = 31 * result + caution.hashCode()
        return result
    }

    override fun toString(): String {
        return "AlertStyling(" +
            "note=$note, " +
            "tip=$tip, " +
            "important=$important, " +
            "warning=$warning, " +
            "caution=$caution" +
            ")"
    }

    /** Companion object for [AlertStyling]. */
    public companion object
}

/**
 * Base styling for a GFM alert, defining the vertical line, title, icon, and text color properties shared across all
 * alert types.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public sealed interface BaseAlertStyling {
    /** The padding applied around the alert content. */
    public val padding: PaddingValues
    /** The width of the vertical accent line. */
    public val lineWidth: Dp
    /** The color of the vertical accent line. */
    public val lineColor: Color
    /** The path effect applied to the vertical accent line, or null for a solid line. */
    public val pathEffect: PathEffect?
    /** The stroke cap style for the vertical accent line. */
    public val strokeCap: StrokeCap
    /** The text style applied to the alert title. */
    public val titleTextStyle: TextStyle
    /** The icon displayed next to the alert title, or null for no icon. */
    public val titleIconKey: IconKey?
    /** The tint color applied to the title icon. */
    public val titleIconTint: Color
    /** The color applied to the alert body text. */
    public val textColor: Color
}

/** Styling for a "note" GFM alert. */
@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class NoteAlertStyling(
    override val padding: PaddingValues,
    override val lineWidth: Dp,
    override val lineColor: Color,
    override val pathEffect: PathEffect?,
    override val strokeCap: StrokeCap,
    override val titleTextStyle: TextStyle,
    override val titleIconKey: IconKey?,
    override val titleIconTint: Color,
    override val textColor: Color,
) : BaseAlertStyling {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NoteAlertStyling

        if (padding != other.padding) return false
        if (lineWidth != other.lineWidth) return false
        if (lineColor != other.lineColor) return false
        if (pathEffect != other.pathEffect) return false
        if (strokeCap != other.strokeCap) return false
        if (titleTextStyle != other.titleTextStyle) return false
        if (titleIconKey != other.titleIconKey) return false
        if (titleIconTint != other.titleIconTint) return false
        if (textColor != other.textColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = padding.hashCode()
        result = 31 * result + lineWidth.hashCode()
        result = 31 * result + lineColor.hashCode()
        result = 31 * result + (pathEffect?.hashCode() ?: 0)
        result = 31 * result + strokeCap.hashCode()
        result = 31 * result + titleTextStyle.hashCode()
        result = 31 * result + (titleIconKey?.hashCode() ?: 0)
        result = 31 * result + titleIconTint.hashCode()
        result = 31 * result + textColor.hashCode()
        return result
    }

    override fun toString(): String {
        return "NoteAlertStyling(" +
            "padding=$padding, " +
            "lineWidth=$lineWidth, " +
            "lineColor=$lineColor, " +
            "pathEffect=$pathEffect, " +
            "strokeCap=$strokeCap, " +
            "titleTextStyle=$titleTextStyle, " +
            "titleIconKey=$titleIconKey, " +
            "titleIconTint=$titleIconTint, " +
            "textColor=$textColor" +
            ")"
    }

    /** Companion object for [NoteAlertStyling]. */
    public companion object
}

/** Styling for a "tip" GFM alert. */
@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class TipAlertStyling(
    override val padding: PaddingValues,
    override val lineWidth: Dp,
    override val lineColor: Color,
    override val pathEffect: PathEffect?,
    override val strokeCap: StrokeCap,
    override val titleTextStyle: TextStyle,
    override val titleIconKey: IconKey?,
    override val titleIconTint: Color,
    override val textColor: Color,
) : BaseAlertStyling {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TipAlertStyling

        if (padding != other.padding) return false
        if (lineWidth != other.lineWidth) return false
        if (lineColor != other.lineColor) return false
        if (pathEffect != other.pathEffect) return false
        if (strokeCap != other.strokeCap) return false
        if (titleTextStyle != other.titleTextStyle) return false
        if (titleIconKey != other.titleIconKey) return false
        if (titleIconTint != other.titleIconTint) return false
        if (textColor != other.textColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = padding.hashCode()
        result = 31 * result + lineWidth.hashCode()
        result = 31 * result + lineColor.hashCode()
        result = 31 * result + (pathEffect?.hashCode() ?: 0)
        result = 31 * result + strokeCap.hashCode()
        result = 31 * result + titleTextStyle.hashCode()
        result = 31 * result + (titleIconKey?.hashCode() ?: 0)
        result = 31 * result + titleIconTint.hashCode()
        result = 31 * result + textColor.hashCode()
        return result
    }

    override fun toString(): String {
        return "TipAlertStyling(" +
            "padding=$padding, " +
            "lineWidth=$lineWidth, " +
            "lineColor=$lineColor, " +
            "pathEffect=$pathEffect, " +
            "strokeCap=$strokeCap, " +
            "titleTextStyle=$titleTextStyle, " +
            "titleIconKey=$titleIconKey, " +
            "titleIconTint=$titleIconTint, " +
            "textColor=$textColor" +
            ")"
    }

    /** Companion object for [TipAlertStyling]. */
    public companion object
}

/** Styling for an "important" GFM alert. */
@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class ImportantAlertStyling(
    override val padding: PaddingValues,
    override val lineWidth: Dp,
    override val lineColor: Color,
    override val pathEffect: PathEffect?,
    override val strokeCap: StrokeCap,
    override val titleTextStyle: TextStyle,
    override val titleIconKey: IconKey?,
    override val titleIconTint: Color,
    override val textColor: Color,
) : BaseAlertStyling {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImportantAlertStyling

        if (padding != other.padding) return false
        if (lineWidth != other.lineWidth) return false
        if (lineColor != other.lineColor) return false
        if (pathEffect != other.pathEffect) return false
        if (strokeCap != other.strokeCap) return false
        if (titleTextStyle != other.titleTextStyle) return false
        if (titleIconKey != other.titleIconKey) return false
        if (titleIconTint != other.titleIconTint) return false
        if (textColor != other.textColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = padding.hashCode()
        result = 31 * result + lineWidth.hashCode()
        result = 31 * result + lineColor.hashCode()
        result = 31 * result + (pathEffect?.hashCode() ?: 0)
        result = 31 * result + strokeCap.hashCode()
        result = 31 * result + titleTextStyle.hashCode()
        result = 31 * result + (titleIconKey?.hashCode() ?: 0)
        result = 31 * result + titleIconTint.hashCode()
        result = 31 * result + textColor.hashCode()
        return result
    }

    override fun toString(): String {
        return "ImportantAlertStyling(" +
            "padding=$padding, " +
            "lineWidth=$lineWidth, " +
            "lineColor=$lineColor, " +
            "pathEffect=$pathEffect, " +
            "strokeCap=$strokeCap, " +
            "titleTextStyle=$titleTextStyle, " +
            "titleIconKey=$titleIconKey, " +
            "titleIconTint=$titleIconTint, " +
            "textColor=$textColor" +
            ")"
    }

    /** Companion object for [ImportantAlertStyling]. */
    public companion object
}

/** Styling for a "warning" GFM alert. */
@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class WarningAlertStyling(
    override val padding: PaddingValues,
    override val lineWidth: Dp,
    override val lineColor: Color,
    override val pathEffect: PathEffect?,
    override val strokeCap: StrokeCap,
    override val titleTextStyle: TextStyle,
    override val titleIconKey: IconKey?,
    override val titleIconTint: Color,
    override val textColor: Color,
) : BaseAlertStyling {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WarningAlertStyling

        if (padding != other.padding) return false
        if (lineWidth != other.lineWidth) return false
        if (lineColor != other.lineColor) return false
        if (pathEffect != other.pathEffect) return false
        if (strokeCap != other.strokeCap) return false
        if (titleTextStyle != other.titleTextStyle) return false
        if (titleIconKey != other.titleIconKey) return false
        if (titleIconTint != other.titleIconTint) return false
        if (textColor != other.textColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = padding.hashCode()
        result = 31 * result + lineWidth.hashCode()
        result = 31 * result + lineColor.hashCode()
        result = 31 * result + (pathEffect?.hashCode() ?: 0)
        result = 31 * result + strokeCap.hashCode()
        result = 31 * result + titleTextStyle.hashCode()
        result = 31 * result + (titleIconKey?.hashCode() ?: 0)
        result = 31 * result + titleIconTint.hashCode()
        result = 31 * result + textColor.hashCode()
        return result
    }

    override fun toString(): String {
        return "WarningAlertStyling(" +
            "padding=$padding, " +
            "lineWidth=$lineWidth, " +
            "lineColor=$lineColor, " +
            "pathEffect=$pathEffect, " +
            "strokeCap=$strokeCap, " +
            "titleTextStyle=$titleTextStyle, " +
            "titleIconKey=$titleIconKey, " +
            "titleIconTint=$titleIconTint, " +
            "textColor=$textColor" +
            ")"
    }

    /** Companion object for [WarningAlertStyling]. */
    public companion object
}

/** Styling for a "caution" GFM alert. */
@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class CautionAlertStyling(
    override val padding: PaddingValues,
    override val lineWidth: Dp,
    override val lineColor: Color,
    override val pathEffect: PathEffect?,
    override val strokeCap: StrokeCap,
    override val titleTextStyle: TextStyle,
    override val titleIconKey: IconKey?,
    override val titleIconTint: Color,
    override val textColor: Color,
) : BaseAlertStyling {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CautionAlertStyling

        if (padding != other.padding) return false
        if (lineWidth != other.lineWidth) return false
        if (lineColor != other.lineColor) return false
        if (pathEffect != other.pathEffect) return false
        if (strokeCap != other.strokeCap) return false
        if (titleTextStyle != other.titleTextStyle) return false
        if (titleIconKey != other.titleIconKey) return false
        if (titleIconTint != other.titleIconTint) return false
        if (textColor != other.textColor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = padding.hashCode()
        result = 31 * result + lineWidth.hashCode()
        result = 31 * result + lineColor.hashCode()
        result = 31 * result + (pathEffect?.hashCode() ?: 0)
        result = 31 * result + strokeCap.hashCode()
        result = 31 * result + titleTextStyle.hashCode()
        result = 31 * result + (titleIconKey?.hashCode() ?: 0)
        result = 31 * result + titleIconTint.hashCode()
        result = 31 * result + textColor.hashCode()
        return result
    }

    override fun toString(): String {
        return "CautionAlertStyling(" +
            "padding=$padding, " +
            "lineWidth=$lineWidth, " +
            "lineColor=$lineColor, " +
            "pathEffect=$pathEffect, " +
            "strokeCap=$strokeCap, " +
            "titleTextStyle=$titleTextStyle, " +
            "titleIconKey=$titleIconKey, " +
            "titleIconTint=$titleIconTint, " +
            "textColor=$textColor" +
            ")"
    }

    /** Companion object for [CautionAlertStyling]. */
    public companion object
}
