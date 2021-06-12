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
 * | $icon $presentable_text (in $container_text) spacer $location_text $location_icon |
 * ```
 * Elements before spacer are aligned to the left, right text and right icon are aligned to the right.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface TargetPresentation {

  companion object {

    @JvmStatic
    fun builder(@Nls presentableText: String): TargetPresentationBuilder {
      return SymbolNavigationService.getInstance().presentationBuilder(presentableText)
    }
  }

  val backgroundColor: Color?

  val icon: Icon?

  val presentableText: @Nls String

  /**
   * Attributes to highlight [presentableText]
   */
  val presentableTextAttributes: TextAttributes?

  val containerText: @Nls String?

  /**
   * Attributes to highlight [containerText]
   */
  val containerTextAttributes: TextAttributes?

  val locationText: @Nls String?

  val locationIcon: Icon?
}
