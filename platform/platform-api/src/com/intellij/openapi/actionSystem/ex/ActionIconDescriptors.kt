// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex

import com.intellij.openapi.actionSystem.ExperimentalIconRendering
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.platform.icons.AllIconDescriptors
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.design.BlendMode
import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.alpha
import com.intellij.platform.icons.modifiers.tintColor
import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus

/**
 * Icon states of an action [Presentation] for the experimental icons pipeline.
 * Call these functions only while [ExperimentalIconRendering.isEnabled] is true.
 */
@ApiStatus.Internal
object ActionIconDescriptors {
  /**
   * Alpha of the disabled variant on a bright theme.
   *
   * This is an estimate of the legacy `GrayFilter` result of the Light theme (`33,-35,100`).
   * The new API has no brightness or contrast modifier, so the variant is a gray tint with an alpha.
   * The legacy filter also reduces the contrast, so no single alpha matches every icon.
   */
  private const val BRIGHT_THEME_DISABLED_ALPHA = 0.5f

  /**
   * Alpha of the disabled variant on a dark theme.
   *
   * This is an estimate of the legacy `GrayFilter` result of the Darcula theme (`-70,-70,100`).
   * It is lower than the bright value. The icons of a dark theme are light on a dark background, so they need more fade to look disabled.
   */
  private const val DARK_THEME_DISABLED_ALPHA = 0.3f

  /**
   * The descriptor to render for [presentation].
   *
   * @return the descriptor of [presentation], [AllIconDescriptors.Toolbar.Unknown] when only a legacy icon is set,
   * or `null` when [presentation] has no icon
   */
  @JvmStatic
  fun effective(presentation: Presentation): IconDescriptor? =
    presentation.iconDescriptor ?: if (presentation.icon != null) AllIconDescriptors.Toolbar.Unknown else null

  /**
   * The disabled variant of [base]: gray and translucent, so it blends into the theme background.
   * The alpha depends on the current theme, so build the variant again after a theme change.
   */
  @JvmStatic
  fun disabled(base: IconDescriptor): IconDescriptor {
    val alpha = if (JBColor.isBright()) BRIGHT_THEME_DISABLED_ALPHA else DARK_THEME_DISABLED_ALPHA
    return iconDescriptor {
      icon(base, IconModifier.tintColor(Color.White, BlendMode.Saturation).alpha(alpha))
    }
  }
}
