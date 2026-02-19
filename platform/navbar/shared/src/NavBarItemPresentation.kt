// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar

import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import javax.swing.Icon

sealed interface NavBarItemPresentation

/**
 * @param text Text to be shown in the navigation bar.
 * @param popupText Text to be shown in the child item popup.
 * @param textAttributes attributes for text and popup text. [SimpleTextAttributes.REGULAR_ATTRIBUTES] is used if [textAttributes] is `null`.
 */
fun NavBarItemPresentation(
  icon: Icon? = null,
  text: @Nls String,
  popupText: @Nls String? = text,
  textAttributes: SimpleTextAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES,
): NavBarItemPresentation {
  return NavBarItemPresentationData(icon, text, popupText, textAttributes, hasContainingFile = false, isModuleContentRoot = false)
}

/**
 * @param hasContainingFile if `true`, [icon] will be displayed, otherwise `false` the icon will be displayed if the item is the last item.
 * @param isModuleContentRoot if `true`, a special icon will be displayed regardless of [icon] and [hasContainingFile].
 */
@Internal // plugins are not supposed to read the presentation, they are only supposed to provide it
data class NavBarItemPresentationData(
  val icon: Icon?,
  val text: @Nls String,
  val popupText: @Nls String?,
  val textAttributes: SimpleTextAttributes?,
  val hasContainingFile: Boolean,
  val isModuleContentRoot: Boolean,
) : NavBarItemPresentation {

  /**
   * Sets whether the item originates from a source file.
   * If `true`, [icon] will be displayed, otherwise, [icon] will be displayed if the item is the last item in the bar.
   *
   * The platform already handles PSI-based navigation items
   *   => plugins are not expected to provide items (and presentation) from PSI
   *   => this flag is not exposed for third-party usage.
   *
   * TODO Find a better way to support this
   */
  fun hasContainingFile(hasContainingFile: Boolean): NavBarItemPresentation {
    return copy(hasContainingFile = hasContainingFile)
  }
}
