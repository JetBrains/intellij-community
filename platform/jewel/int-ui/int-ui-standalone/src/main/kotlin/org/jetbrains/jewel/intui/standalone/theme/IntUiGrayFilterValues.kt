// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.theme

import org.jetbrains.jewel.foundation.DisabledAppearanceValues

/**
 * Creates a [DisabledAppearanceValues] instance with default settings suitable for a light UI theme, mirroring the
 * default IntelliJ Platform LaF light theme.
 *
 * @param brightness The brightness adjustment.
 * @param contrast The contrast adjustment.
 * @param alpha The alpha multiplier.
 * @return A new [DisabledAppearanceValues] instance configured for a light theme.
 * @see DisabledAppearanceValues
 */
public fun DisabledAppearanceValues.Companion.light(
    brightness: Int = 33,
    contrast: Int = -35,
    alpha: Int = 100,
): DisabledAppearanceValues = DisabledAppearanceValues(brightness, contrast, alpha)

/**
 * Creates a [DisabledAppearanceValues] instance with default settings suitable for a dark UI theme, mirroring the
 * default IntelliJ Platform Darcula theme.
 *
 * @param brightness The brightness adjustment.
 * @param contrast The contrast adjustment.
 * @param alpha The alpha multiplier.
 * @return A new [DisabledAppearanceValues] instance configured for a light theme.
 * @see DisabledAppearanceValues
 */
public fun DisabledAppearanceValues.Companion.dark(
    brightness: Int = -70,
    contrast: Int = -70,
    alpha: Int = 100,
): DisabledAppearanceValues = DisabledAppearanceValues(brightness, contrast, alpha)
