// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex

@Deprecated("Deprecated in favour of tracker-independant LineStatusMarkerRendererWithPopup",
            ReplaceWith("LineStatusMarkerRendererWithPopup",
                        "com.intellij.openapi.vcs.ex.LineStatusMarkerRendererWithPopup"))
open class LineStatusMarkerPopupRenderer(@JvmField protected val myTracker: LineStatusTrackerI<*>)
  : LineStatusTrackerMarkerRenderer(myTracker) {

  override fun toString(): String = "LineStatusMarkerPopupRenderer(tracker=$myTracker)"
}
