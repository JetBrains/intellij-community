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
@ApiStatus.NonExtendable
abstract class LcrTextInitParams(foreground: Color) : LcrInitParams() {

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
  @Suppress("CanBePrimaryConstructorProperty")
  var foreground: Color = foreground

  /**
   * Attributes of the text, if set then [foreground] is ignored
   */
  var attributes: SimpleTextAttributes? = null

  var font: Font? = UIManager.getFont("Label.font")

  @ApiStatus.Internal
  @ApiStatus.Experimental
  var renderingHints: Map<RenderingHints.Key, Any?>? = null

  /**
   * The text is used by speed search and therefore should be highlighted while searching.
   * The following rules should be used to comply with IJ UX standards:
   * * The speed search filters out items from the list: only the current (selected) item shows the speed search highlighting
   * * The speed search doesn't filter out items from the list: all items show the speed search highlighting
   */
  abstract fun speedSearch(init: LcrTextSpeedSearchParams.() -> Unit)
}
