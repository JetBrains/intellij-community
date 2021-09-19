// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.track.active

import com.intellij.feedback.state.active.LastActive.trackActive
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener

/**
 * Tracks mouse activity in the editor and display the notification after a long inactivity.
 */

class EditorMouseEventTracker : EditorMouseListener, EditorMouseMotionListener {
  override fun mouseClicked(event: EditorMouseEvent) {
    trackActive(event.editor.project ?: return)
  }

  override fun mouseMoved(event: EditorMouseEvent) {
    trackActive(event.editor.project ?: return)
  }
}