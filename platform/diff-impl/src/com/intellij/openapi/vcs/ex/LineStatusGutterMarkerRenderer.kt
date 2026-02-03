// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.openapi.diff.DefaultFlagsProvider
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Rectangle

@ApiStatus.Internal
abstract class LineStatusGutterMarkerRenderer : LineMarkerRendererEx {

  /**
   * Get a list of ranges to be painted or acted upon
   *
   * @return null if painting is disabled
   */
  protected abstract fun getPaintedRanges(): List<Range>?

  override fun getPosition(): LineMarkerRendererEx.Position = LineMarkerRendererEx.Position.CUSTOM

  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    val ranges = getPaintedRanges() ?: return
    LineStatusMarkerDrawUtil.paintDefault(editor, g, ranges, DefaultFlagsProvider.DEFAULT, 0)
  }
}