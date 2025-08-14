// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * An immutable value holder for the parameters required to create a "gray filter" effect, typically used to render UI
 * elements in a disabled state.
 *
 * This class encapsulates the `brightness`, `contrast`, and `alpha` values needed by the [Modifier.disabledAppearance]
 * composable. By marking it as `@Immutable`, we signal to the Compose compiler that it can safely skip recompositions
 * of components that consume this class if its instance has not changed.
 *
 * The parameter ranges are validated upon instantiation to prevent invalid values.
 *
 * @property brightness The brightness adjustment, must be in the range [-100, 100].
 * @property contrast The contrast adjustment, must be in the range [-100, 100].
 * @property alpha The alpha multiplier, must be in the range [0, 100].
 * @see LocalDisabledAppearanceValues
 * @see Modifier.disabledAppearance
 */
@Immutable
@GenerateDataFunctions
public class DisabledAppearanceValues(public val brightness: Int, public val contrast: Int, public val alpha: Int) {
    init {
        require(brightness in -100..100) { "The brightness must be in [-100, 100], but was $brightness" }
        require(contrast in -100..100) { "The contrast must be in [-100, 100], but was $contrast" }
        require(alpha in 0..100) { "The alpha must be in [0, 100], but was $alpha" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DisabledAppearanceValues

        if (brightness != other.brightness) return false
        if (contrast != other.contrast) return false
        if (alpha != other.alpha) return false

        return true
    }

    override fun hashCode(): Int {
        var result = brightness
        result = 31 * result + contrast
        result = 31 * result + alpha
        return result
    }

    override fun toString(): String = "GrayFilterValues(brightness=$brightness, contrast=$contrast, alpha=$alpha)"

    public companion object
}

public val LocalDisabledAppearanceValues: ProvidableCompositionLocal<DisabledAppearanceValues> =
    staticCompositionLocalOf {
        error("No DisabledAppearanceValues provided. Have you forgotten the theme?")
    }
