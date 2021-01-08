// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation

import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.Icon

/**
 * Represents presentation in target popup as follows:
 * ```
 * | $icon $presentable_text (in $location_text) spacer $right_text $right_icon |
 * ```
 * Elements before spacer are aligned to the left, right text and right icon are aligned to the right.
 */
@ApiStatus.Experimental
interface TargetPopupPresentation {

  @JvmDefault
  val backgroundColor: Color?
    get() = null

  @JvmDefault
  val icon: Icon?
    get() = null

  val presentableText: @Nls String

  /**
   * Attributes to highlight [presentableText]
   */
  @JvmDefault
  val presentableTextAttributes: TextAttributes?
    get() = null

  @JvmDefault
  val locationText: @Nls String?
    get() = null

  /**
   * Attributes to highlight [locationText]
   */
  @JvmDefault
  val locationTextAttributes: TextAttributes?
    get() = null

  @JvmDefault
  val rightText: @Nls String?
    get() = null

  @JvmDefault
  val rightIcon: Icon?
    get() = null
}
