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

@ApiStatus.Experimental
@ExperimentalJewelApi
@GenerateDataFunctions
public class AlertStyling(
    public val note: NoteAlertStyling,
    public val tip: TipAlertStyling,
    public val important: ImportantAlertStyling,
    public val warning: WarningAlertStyling,
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

    public companion object
}

@ApiStatus.Experimental
@ExperimentalJewelApi
public sealed interface BaseAlertStyling {
    public val padding: PaddingValues
    public val lineWidth: Dp
    public val lineColor: Color
    public val pathEffect: PathEffect?
    public val strokeCap: StrokeCap
    public val titleTextStyle: TextStyle
    public val titleIconKey: IconKey?
    public val titleIconTint: Color
    public val textColor: Color
}

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

    public companion object
}

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

    public companion object
}

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

    public companion object
}

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

    public companion object
}

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

    public companion object
}
