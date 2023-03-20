// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar

import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Data needed to present a navigation bar item within the bar itself or a popup menu.
 */
class NavBarItemPresentation(

  /**
   * Icon to be shown
   */
  val icon: Icon?,

  /**
   * A mandatory text to be shown in the navigation bar
   */
  val text: @Nls String,

  /**
   * An optional text to be shown in a popup menu. Falling back to <link>text</link> if <code>null</code>.
   */
  val popupText: @Nls String?,

  /**
   * Text attributes to highlight the text.
   */
  val textAttributes: SimpleTextAttributes,

  /**
   * Not used, tobe deleted
   */
  @Deprecated("not used, to be deleted")
  val selectedTextAttributes: SimpleTextAttributes,

  /**
   * Find better place for that.
   * Currently used as one more criteria to show item's icon
   */
  val hasContainingFile: Boolean,
)
