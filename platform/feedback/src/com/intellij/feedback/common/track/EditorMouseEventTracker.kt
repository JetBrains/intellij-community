// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.track

import com.intellij.feedback.common.IdleFeedbackTypeResolver
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener

/**
 * Tracks mouse activity in the editor and display the notification after a long inactivity.
 */

class EditorMouseEventTracker : EditorMouseListener, EditorMouseMotionListener {
  override fun mouseClicked(event: EditorMouseEvent) {
    IdleFeedbackTypeResolver.checkActivity(event.editor.project)
  }

  override fun mouseMoved(event: EditorMouseEvent) {
    IdleFeedbackTypeResolver.checkActivity(event.editor.project)
  }
}