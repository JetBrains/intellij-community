// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.presentation

import com.intellij.navigation.SymbolNavigationService
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.Icon

/**
 * Represents presentation in target popup as follows:
 * ```
 * | $icon$ $presentableText$ $containerText$ spacer $locationText$ $locationIcon$ |
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

    /**
     * @return a builder instance initialized from existing [presentation]
     */
    @JvmStatic
    fun builder(presentation: TargetPresentation): TargetPresentationBuilder {
      // the only implementation is also a builder
      return presentation as TargetPresentationBuilder
    }
  }

  val backgroundColor: Color?

  val icon: Icon?

  val presentableText: @Nls String

  /**
   * Attributes to highlight [presentableText]
   */
  val presentableTextAttributes: TextAttributes?

  /**
   * Presentable text of a container, e.g. containing class name for a method, or a parent directory name for a file
   */
  val containerText: @Nls String?

  /**
   * Attributes to highlight [containerText]
   */
  val containerTextAttributes: TextAttributes?

  /**
   * Presentable text of a location, e.g. a containing module, or a library, or an SDK
   */
  val locationText: @Nls String?

  /**
   * Icon of a location, e.g. a containing module, or a library, or an SDK
   */
  val locationIcon: Icon?
}
