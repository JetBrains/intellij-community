// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.rendering.ThemeContext

/**
 * The dark state of this theme: [ThemeContext.isDark]. A theme that only overrides the deprecated
 * [ThemeContext.imageModifiers] gives the dark state of those modifiers. `null` if the theme does not supply one.
 */
fun ThemeContext.darkState(): Boolean? = isDark() ?: legacyImageModifiers()?.isDark

/** The modifiers of the deprecated [ThemeContext.imageModifiers], for a theme that still overrides it. */
internal fun ThemeContext.legacyImageModifiers(): DefaultImageModifiers? {
    @Suppress("DEPRECATION")
    return imageModifiers() as? DefaultImageModifiers
}
