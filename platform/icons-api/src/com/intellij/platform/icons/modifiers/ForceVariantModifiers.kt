// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.modifiers

import com.intellij.platform.icons.IconManager

/**
 * Makes the icon use its dark artwork, whatever the theme says.
 * Without a modifier of this kind, the theme chooses the artwork.
 *
 * Use it where the background does not follow the theme, for example a dark menu bar in a light theme.
 * An icon without separate dark artwork looks the same as without the modifier.
 *
 * @see forceLightVariant
 */
fun IconModifier.forceDarkVariant(): IconModifier = this then IconManager.modifiers().forceDarkVariant(isDark = true)

/**
 * Makes the icon use its light artwork, even in a dark theme.
 * Without a modifier of this kind, the theme chooses the artwork.
 *
 * Use it where the background does not follow the theme, for example a light menu bar in a dark theme.
 *
 * @see forceDarkVariant
 */
fun IconModifier.forceLightVariant(): IconModifier = this then IconManager.modifiers().forceDarkVariant(isDark = false)
