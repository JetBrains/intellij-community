// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.modifiers

import com.intellij.platform.icons.IconManager

/**
 * Sets the artwork variant for the image layers this modifier applies to, including those nested inside (nested icons,
 * layouts, animation frames). `dark(true)` uses the dark artwork (the `_dark` files), and `dark(false)` uses the light
 * artwork, whatever the current theme is.
 *
 * The innermost explicit setting wins. Without one, the image layers follow the dark state of the current theme.
 */
fun IconModifier.dark(dark: Boolean): IconModifier = this then IconManager.modifiers().dark(dark)
