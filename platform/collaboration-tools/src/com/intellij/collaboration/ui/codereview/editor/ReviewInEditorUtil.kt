// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.openapi.diff.LineStatusMarkerColorScheme
import com.intellij.openapi.editor.Editor
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import java.awt.Color

object ReviewInEditorUtil {
  val REVIEW_CHANGES_STATUS_COLOR = JBColor.namedColor("Review.Editor.Line.Status.Marker", ColorUtil.fromHex("#A177F4"))

  val REVIEW_STATUS_MARKER_COLOR_SCHEME = object : LineStatusMarkerColorScheme() {
    override fun getColor(editor: Editor, type: Byte): Color = REVIEW_CHANGES_STATUS_COLOR
    override fun getIgnoredBorderColor(editor: Editor, type: Byte): Color = REVIEW_CHANGES_STATUS_COLOR
    override fun getErrorStripeColor(type: Byte): Color = REVIEW_CHANGES_STATUS_COLOR
  }
}