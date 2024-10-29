// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Segment
import com.intellij.util.Alarm
import com.intellij.util.SingleAlarm
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class RangeBlinker(
  private val editor: Editor,
  private val attributes: TextAttributes,
  private var timeToLive: Int,
  parentDisposable: Disposable?,
) {
  private val markers = ArrayList<Segment>()
  private var show = true
  private val blinkingAlarm = SingleAlarm(
    threadToUse = Alarm.ThreadToUse.SWING_THREAD,
    parentDisposable = parentDisposable,
    delay = 400,
    task = {
      if (timeToLive > 0 || show) {
        timeToLive--
        show = !show
        startBlinking()
      }
    })
  private val addedHighlighters = ArrayList<RangeHighlighter>()

  fun resetMarkers(markers: List<Segment>) {
    removeHighlights()
    this.markers.clear()
    stopBlinking()
    this.markers.addAll(markers)
    show = true
  }

  private fun removeHighlights() {
    val markupModel = editor.markupModel
    val allHighlighters = markupModel.allHighlighters

    for (highlighter in addedHighlighters) {
      if (allHighlighters.indexOf(highlighter) != -1) {
        highlighter.dispose()
      }
    }
    addedHighlighters.clear()
  }

  fun startBlinking() {
    val project = editor.project
    if (ApplicationManager.getApplication().isDisposed || editor.isDisposed || project != null && project.isDisposed) {
      return
    }

    val markupModel = editor.markupModel
    if (show) {
      for (segment in markers) {
        if (segment.endOffset > editor.document.textLength) {
          continue
        }

        val highlighter = markupModel.addRangeHighlighter(segment.startOffset, segment.endOffset,
                                                          HighlighterLayer.ADDITIONAL_SYNTAX, attributes,
                                                          HighlighterTargetArea.EXACT_RANGE)
        addedHighlighters.add(highlighter)
      }
    }
    else {
      removeHighlights()
    }
    blinkingAlarm.scheduleCancelAndRequest()
  }

  fun stopBlinking() {
    blinkingAlarm.cancel()
  }
}
