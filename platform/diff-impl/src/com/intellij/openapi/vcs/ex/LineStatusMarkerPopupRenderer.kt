// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex

import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.NonNls
import java.awt.Point

@Deprecated("Deprecated in favour of tracker-independant LineStatusMarkerRendererWithPopup",
            ReplaceWith("LineStatusMarkerRendererWithPopup",
                        "com.intellij.openapi.vcs.ex.LineStatusMarkerRendererWithPopup"))
open class LineStatusMarkerPopupRenderer(@JvmField protected val myTracker: LineStatusTrackerI<*>)
  : LineStatusTrackerMarkerRenderer(myTracker) {

  @Deprecated("Use non-inner variant in com.intellij.openapi.vcs.ex.LineStatusMarkerPopupActions",
              ReplaceWith("LineStatusMarkerPopupActions.RangeMarkerAction",
                          "com.intellij.openapi.vcs.ex.LineStatusMarkerPopupActions"))
  abstract inner class RangeMarkerAction(editor: Editor, range: Range, actionId: @NonNls String?)
    : LineStatusMarkerPopupActions.RangeMarkerAction(editor, myTracker, range, actionId)

  @Deprecated("Use non-inner variant in com.intellij.openapi.vcs.ex.LineStatusMarkerPopupActions",
              ReplaceWith("LineStatusMarkerPopupActions.ShowNextChangeMarkerAction",
                          "com.intellij.openapi.vcs.ex.LineStatusMarkerPopupActions"))
  inner class ShowNextChangeMarkerAction(editor: Editor, range: Range)
    : LineStatusMarkerPopupActions.ShowNextChangeMarkerAction(editor, myTracker, range, this)

  @Deprecated("Use non-inner variant in com.intellij.openapi.vcs.ex.LineStatusMarkerPopupActions",
              ReplaceWith("LineStatusMarkerPopupActions.ShowPrevChangeMarkerAction",
                          "com.intellij.openapi.vcs.ex.LineStatusMarkerPopupActions"))
  inner class ShowPrevChangeMarkerAction(editor: Editor, range: Range)
    : LineStatusMarkerPopupActions.ShowPrevChangeMarkerAction(editor, myTracker, range, this)

  @Deprecated("Use non-inner variant in com.intellij.openapi.vcs.ex.LineStatusMarkerPopupActions",
              ReplaceWith("LineStatusMarkerPopupActions.CopyLineStatusRangeAction",
                          "com.intellij.openapi.vcs.ex.LineStatusMarkerPopupActions"))
  inner class CopyLineStatusRangeAction(editor: Editor, range: Range)
    : LineStatusMarkerPopupActions.CopyLineStatusRangeAction(editor, myTracker, range)

  @Deprecated("Use non-inner variant in com.intellij.openapi.vcs.ex.LineStatusMarkerPopupActions",
              ReplaceWith("LineStatusMarkerPopupActions.ShowLineStatusRangeDiffAction",
                          "com.intellij.openapi.vcs.ex.LineStatusMarkerPopupActions"))
  inner class ShowLineStatusRangeDiffAction(editor: Editor, range: Range)
    : LineStatusMarkerPopupActions.ShowLineStatusRangeDiffAction(editor, myTracker, range)

  @Deprecated("Use non-inner variant in com.intellij.openapi.vcs.ex.LineStatusMarkerPopupActions",
              ReplaceWith("LineStatusMarkerPopupActions.ToggleByWordDiffAction",
                          "com.intellij.openapi.vcs.ex.LineStatusMarkerPopupActions"))
  inner class ToggleByWordDiffAction(editor: Editor, range: Range, mousePosition: Point?)
    : LineStatusMarkerPopupActions.ToggleByWordDiffAction(editor, myTracker, range, mousePosition, this)

  override fun toString(): String = "LineStatusMarkerPopupRenderer(tracker=$myTracker)"
}
