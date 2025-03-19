// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.impl.track

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.impl.IdleFeedbackResolver
import com.intellij.psi.PsiFile
import java.time.Duration
import java.time.LocalDateTime

/**
 * Tracks keyboard and mouse activity in any editor and show a feedback notification after a long inactivity.
 */
class EditorInputEventTracker : TypedHandlerDelegate(), EditorMouseListener, EditorMouseMotionListener {

  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun mouseClicked(event: EditorMouseEvent) {
    checkActivity(event.editor.project)
  }

  override fun mouseMoved(event: EditorMouseEvent) {
    checkActivity(event.editor.project)
  }

  override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
    checkActivity(project)
    return Result.CONTINUE
  }

  private fun checkActivity(project: Project?) {
    if (Registry.getInstance().isLoaded &&
        Duration.between(lastActivityTime, LocalDateTime.now()).toSeconds() >=
        Registry.intValue("platform.feedback.time.to.show.notification", MIN_INACTIVE_TIME)) {
      IdleFeedbackResolver.getInstance().showFeedbackNotification(project)
    }
    lastActivityTime = LocalDateTime.now()
  }
}

// 10 minutes
private const val MIN_INACTIVE_TIME = 600

private var lastActivityTime: LocalDateTime = LocalDateTime.now()