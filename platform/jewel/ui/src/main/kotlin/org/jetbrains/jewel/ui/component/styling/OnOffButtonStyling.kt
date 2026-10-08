// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.PathIconKey

/**
 * Combines the metrics and the icons that define the visual style of an on/off button (a.k.a. toggle).
 *
 * The button paints the same images as the IntelliJ Platform toggle. The images carry the colors and the focus ring, so
 * a theme can change both the colors and the images themselves.
 */
@Immutable
@GenerateDataFunctions
public class OnOffButtonStyle(
    /** The size metrics for the on/off button. */
    public val metrics: OnOffButtonMetrics,
    /** The icon keys for the on/off button. */
    public val icons: OnOffButtonIcons,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OnOffButtonStyle

        if (metrics != other.metrics) return false
        if (icons != other.icons) return false

        return true
    }

    override fun hashCode(): Int {
        var result = metrics.hashCode()
        result = 31 * result + icons.hashCode()
        return result
    }

    override fun toString(): String = "OnOffButtonStyle(metrics=$metrics, icons=$icons)"

    /** Companion object for [OnOffButtonStyle]. */
    public companion object
}

/** Holds the size metrics for the on/off button. */
@Immutable
@GenerateDataFunctions
public class OnOffButtonMetrics(
    /** The size of the on/off button. It includes the room for the focus ring around the track. */
    public val size: DpSize
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OnOffButtonMetrics

        return size == other.size
    }

    override fun hashCode(): Int = size.hashCode()

    override fun toString(): String = "OnOffButtonMetrics(size=$size)"

    /** Companion object for [OnOffButtonMetrics]. */
    public companion object
}

/**
 * Holds the icon key for the on/off button.
 *
 * The button picks the image variant from the key with suffixes, in this order: `On` or `Off`, then `Focused` or
 * `Disabled`. For the default key `toggle.svg`, the images are `toggleOn.svg`, `toggleOffFocused.svg`,
 * `toggleOnDisabled.svg`, and so on.
 */
@Immutable
@GenerateDataFunctions
public class OnOffButtonIcons(
    /** The base icon key for the on/off button images. */
    public val onOffButton: IconKey
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OnOffButtonIcons

        return onOffButton == other.onOffButton
    }

    override fun hashCode(): Int = onOffButton.hashCode()

    override fun toString(): String = "OnOffButtonIcons(onOffButton=$onOffButton)"

    /** Companion object for [OnOffButtonIcons]. */
    public companion object
}

/** Creating a fallback style for compatibility with older versions. */
internal fun fallbackOnOffButtonStyle(): OnOffButtonStyle =
    OnOffButtonStyle(
        metrics = OnOffButtonMetrics(size = DpSize(32.dp, 22.dp)),
        icons =
            OnOffButtonIcons(
                onOffButton = PathIconKey("com/intellij/ide/ui/laf/icons/toggle.svg", OnOffButtonIcons::class.java)
            ),
    )

/** Provides the [OnOffButtonStyle] to use for on/off buttons in the current composition. */
public val LocalOnOffButtonStyle: ProvidableCompositionLocal<OnOffButtonStyle> = staticCompositionLocalOf {
    error("No OnOffButtonStyle provided. Have you forgotten the theme?")
}
