// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.ex

import com.intellij.openapi.actionSystem.ExperimentalIconRendering
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.platform.icons.AllIconDescriptors
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.grayFilter
import com.intellij.util.ui.GrayFilter
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Icon states of an action [Presentation] for the experimental icons pipeline. Call these functions only while
 * [ExperimentalIconRendering.isEnabled] is true.
 */
@ApiStatus.Internal
object ActionIconDescriptors {
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
   * The disabled variant of [base]. It has the same pixels as `IconLoader.getDisabledIcon` gives for the legacy icon. It uses the gray
   * filter of the look and feel, [UIUtil.getGrayFilter]. Build the variant again after a theme change.
   */
  @JvmStatic
  fun disabled(base: IconDescriptor): IconDescriptor {
    val filter = UIUtil.getGrayFilter() as GrayFilter
    return iconDescriptor { icon(base, IconModifier.grayFilter(filter.brightness, filter.contrast, filter.alpha)) }
  }
}
