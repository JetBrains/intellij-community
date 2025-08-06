// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.providers.scroll

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import java.awt.event.AdjustmentListener

@OptIn(FlowPreview::class)
class NotebookEditorScrollEndDetector private constructor(
  // PY-73713
  private val editor: EditorImpl,
) : Disposable {

  val scrollFlow: MutableSharedFlow<Unit> = MutableSharedFlow<Unit>(
    extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  val debouncedScrollFlow: Flow<Unit> = scrollFlow.debounce(AFTER_SCROLL_DELAY_MS)

  private val adjustmentListener = AdjustmentListener { e ->
    scrollFlow.tryEmit(Unit)
  }

  init {
    editor.scrollPane.verticalScrollBar.addAdjustmentListener(adjustmentListener)
  }

  override fun dispose() {
    editor.scrollPane.verticalScrollBar.removeAdjustmentListener(adjustmentListener)
    editor.putUserData(SCROLL_END_DETECTOR_KEY, null)
  }

  companion object {
    private val SCROLL_END_DETECTOR_KEY = Key.create<NotebookEditorScrollEndDetector>("SCROLL_END_DETECTOR")
    private const val AFTER_SCROLL_DELAY_MS = 150L

    fun install(editor: EditorImpl) {
      if (ApplicationManager.getApplication().isUnitTestMode) return
      val manager = NotebookEditorScrollEndDetector(editor)
      editor.putUserData(SCROLL_END_DETECTOR_KEY, manager)
      Disposer.register(editor.disposable, manager)
    }

    fun get(editor: EditorImpl): NotebookEditorScrollEndDetector? = editor.getUserData(SCROLL_END_DETECTOR_KEY)
  }
}