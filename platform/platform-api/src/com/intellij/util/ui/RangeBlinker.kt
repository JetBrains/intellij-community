// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Segment
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal

@Service(Service.Level.APP)
private class RangeBlinkerService(val coroutineScope: CoroutineScope)

@Internal
class RangeBlinker(
  private val editor: Editor,
  private val attributes: TextAttributes,
  private var timeToLive: Int,
  parentDisposable: Disposable?,
) {
  private val lifetime = timeToLive
  private val markers = ArrayList<Segment>()
  private var show = true
  private val addedHighlighters = ArrayList<RangeHighlighter>()

  private val scope: CoroutineScope = service<RangeBlinkerService>().coroutineScope.childScope("RangeBlinker")
  private val triggerFlow = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
  private var blinkingJob: Job? = null

  init {
    if (parentDisposable != null) {
      Disposer.register(parentDisposable) { scope.cancel() }
    }
  }

  fun resetMarkers(markers: List<Segment>, resetTime: Boolean = false) {
    removeHighlights()
    this.markers.clear()
    stopBlinking()
    this.markers.addAll(markers)
    show = true
    if (resetTime) timeToLive = lifetime
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

  private fun doBlinkTick() {
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

    if (timeToLive > 0 || show) {
      timeToLive--
      show = !show
      // Chain next tick after delay via flow
      triggerFlow.tryEmit(Unit)
    } else {
      stopBlinking()
    }
  }

  @OptIn(FlowPreview::class)
  fun startBlinking() {
    if (blinkingJob == null) {
      blinkingJob = scope.launch {
        doBlinkTick()
        triggerFlow
          .debounce(400)
          .sample(400)
          .collect { doBlinkTick() }
      }
    }
    else {
      triggerFlow.tryEmit(Unit)
    }
  }

  fun stopBlinking() {
    blinkingJob?.cancel()
    removeHighlights()
    blinkingJob = null
  }
}
