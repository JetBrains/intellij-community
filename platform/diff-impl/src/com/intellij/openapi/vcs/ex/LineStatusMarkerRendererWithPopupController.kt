// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus
import java.awt.Point

@ApiStatus.Experimental
interface LineStatusMarkerRendererWithPopupController {
  /**
   * Scroll [editor] view and move caret to the specified [range] and show the popup
   */
  fun scrollAndShow(editor: Editor, range: Range)

  /**
   * Show the popup for specified [range] after [editor] scrolling model settles down
   */
  fun showAfterScroll(editor: Editor, range: Range)

  /**
   * Show the popup for specified [range] at [mousePosition]
   */
  fun showHintAt(editor: Editor, range: Range, mousePosition: Point?)

  /**
   * Find the new mapping for [range] and show the popup for this new range at [mousePosition]
   */
  fun reopenRange(editor: Editor, range: Range, mousePosition: Point?)
}