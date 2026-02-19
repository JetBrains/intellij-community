// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

@Immutable
@GenerateDataFunctions
public class SpeedSearchStyle(
    public val colors: SpeedSearchColors,
    public val metrics: SpeedSearchMetrics,
    public val icons: SpeedSearchIcons,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SpeedSearchStyle

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

    override fun toString(): String = "SpeedSearchStyle(colors=$colors, metrics=$metrics, icons=$icons)"

    public companion object
}

@Immutable
@GenerateDataFunctions
public class SpeedSearchColors(
    public val background: Color,
    public val border: Color,
    public val foreground: Color,
    public val error: Color,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SpeedSearchColors

        if (background != other.background) return false
        if (border != other.border) return false
        if (foreground != other.foreground) return false
        if (error != other.error) return false

        return true
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + border.hashCode()
        result = 31 * result + foreground.hashCode()
        result = 31 * result + error.hashCode()
        return result
    }

    override fun toString(): String =
        "SpeedSearchColors(background=$background, border=$border, foreground=$foreground, error=$error)"

    public companion object
}

@Immutable
@GenerateDataFunctions
public class SpeedSearchMetrics(public val contentPadding: PaddingValues) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SpeedSearchMetrics

        return contentPadding == other.contentPadding
    }

    override fun hashCode(): Int = contentPadding.hashCode()

    override fun toString(): String = "SpeedSearchMetrics(contentPadding=$contentPadding)"

    public companion object
}

@Immutable
@GenerateDataFunctions
public class SpeedSearchIcons(public val magnifyingGlass: IconKey) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SpeedSearchIcons

        return magnifyingGlass == other.magnifyingGlass
    }

    override fun hashCode(): Int = magnifyingGlass.hashCode()

    override fun toString(): String = "SpeedSearchIcons(magnifyingGlass=$magnifyingGlass)"

    public companion object
}

public val LocalSpeedSearchStyle: ProvidableCompositionLocal<SpeedSearchStyle> = staticCompositionLocalOf {
    error("No default SpeedSearchStyle provided. Have you forgotten the theme?")
}

/** Creating fallback style for compatibility with older versions. */
internal fun fallbackSpeedSearchStyle() =
    SpeedSearchStyle(
        colors =
            SpeedSearchColors(
                background = Color(0xFFFFFFFF),
                border = Color(0xFFEBECF0),
                foreground = Color.Unspecified,
                error = Color(0xFFDB3B4B),
            ),
        metrics = SpeedSearchMetrics(PaddingValues(4.dp)),
        icons = SpeedSearchIcons(AllIconsKeys.Actions.Search),
    )
