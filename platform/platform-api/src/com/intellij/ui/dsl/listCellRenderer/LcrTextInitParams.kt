// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer

import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import javax.swing.UIManager

@ApiStatus.Experimental
class LcrTextInitParams(foreground: Color) : LcrInitParams() {

  /**
   * A gray text that is usually used for non-primary information in renderers
   */
  val greyForeground: Color
    get() = NamedColorUtil.getInactiveTextColor()

  /**
   * Foreground of the text, used only if [attributes] are not specified
   *
   * See also [greyForeground]
   */
  var foreground: Color = foreground

  /**
   * Attributes of the text, if set then [foreground] is ignored
   */
  var attributes: SimpleTextAttributes? = null

  var font: Font? = UIManager.getFont("Label.font")

  /**
   * true if the text is used by speed search and therefore should be highlighted while searching
   */
  var speedSearchHighlighting: Boolean = false

  @ApiStatus.Internal
  @ApiStatus.Experimental
  val renderingHints: MutableMap<RenderingHints.Key, Any?> = getDefaultTextRenderingHints()
}
