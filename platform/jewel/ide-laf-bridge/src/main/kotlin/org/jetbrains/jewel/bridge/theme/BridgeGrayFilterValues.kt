// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.theme

import com.intellij.util.ui.GrayFilter
import com.intellij.util.ui.UIUtil
import org.jetbrains.jewel.foundation.DisabledAppearanceValues

/**
 * Creates a [DisabledAppearanceValues] instance by reading the disabled state appearance settings from the current
 * IntelliJ Look and Feel (LaF).
 *
 * This function acts as a bridge between the traditional Swing-based LaF settings and the Compose UI framework. It
 * attempts to read the current disabled "gray filter" defined by the active theme using [UIUtil.getGrayFilter].
 *
 * If the active LaF provides an instance of [com.intellij.util.ui.GrayFilter], its `brightness`, `contrast`, and
 * `alpha` values are extracted directly. This ensures that the Compose disabled appearance perfectly matches the rest
 * of the IntelliJ platform UI.
 *
 * If the filter from the LaF is not a [com.intellij.util.ui.GrayFilter] or is null, this function provides sensible
 * default values that differ for dark and light themes to ensure a consistent and legible disabled appearance. The
 * light theme default value is from IntelliJLaF theme, while the dark theme default value is from the Darcula theme.
 *
 * @return A [DisabledAppearanceValues] instance containing the resolved brightness, contrast, and alpha values, ready
 *   to be provided to a CompositionLocal like `LocalGrayFilterValues`.
 * @see DisabledAppearanceValues
 */
@Suppress("UnstableApiUsage")
public fun DisabledAppearanceValues.Companion.readFromLaF(): DisabledAppearanceValues {
    val grayFilter = UIUtil.getGrayFilter()
    val (brightness, contrast, alpha) =
        if (grayFilter is GrayFilter) {
            listOf(grayFilter.brightness, grayFilter.contrast, grayFilter.alpha)
        } else {
            if (isDark) listOf(-70, -70, 100) else listOf(33, -35, 100)
        }

    return DisabledAppearanceValues(brightness, contrast, alpha)
}
