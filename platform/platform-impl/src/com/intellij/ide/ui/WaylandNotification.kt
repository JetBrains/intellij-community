// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.AppMode
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import com.intellij.util.ui.StartupUiUtil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

internal class WaylandUserActivityTracker : TypedHandlerDelegate(), EditorMouseListener, EditorMouseMotionListener {
  override fun mouseClicked(event: EditorMouseEvent) {
    userActivityHappened(event.editor.project)
  }

  override fun mouseMoved(e: EditorMouseEvent) {
    userActivityHappened(e.editor.project)
  }

  override fun charTyped(
    c: Char,
    project: Project,
    editor: Editor,
    file: PsiFile,
  ): Result {
    userActivityHappened(project)
    return Result.CONTINUE
  }

  private fun userActivityHappened(project: Project?) {
    if (!StartupUiUtil.isWaylandToolkit()) return
    if (AppMode.isRemoteDevHost()) return // these notification are only for the frontend or the monolith
    // The state is extracted into a separate service because this helper class can have several instances
    // (working in different roles: a mouse listener, a typing listener...).
    WaylandNotificationService.getInstance().userActivityHappened(project)
  }
}

@Service(Service.Level.APP)
internal class WaylandNotificationService {
  private var firstActivityTime: TimeSource.Monotonic.ValueTimeMark? = null
  private var previousActivityTime: TimeSource.Monotonic.ValueTimeMark? = null
  private var notificationAlreadyShown: Boolean? = null

  companion object {
    @JvmStatic fun getInstance(): WaylandNotificationService = service()
  }

  fun userActivityHappened(project: Project?) {
    if (isNotificationAlreadyShown()) return

    val currentActivityTime = TimeSource.Monotonic.markNow()

    var firstActivityTime = firstActivityTime
    if (firstActivityTime == null) {
      firstActivityTime = currentActivityTime
      this.firstActivityTime = firstActivityTime
    }

    val previousActivityTime = previousActivityTime
    if (Registry.getInstance().isLoaded && previousActivityTime != null) {
      val sinceFirstActivity = currentActivityTime - firstActivityTime
      val sincePreviousActivity = currentActivityTime - previousActivityTime
      if (
        sinceFirstActivity >= delaySinceFirstActivity() &&
        sincePreviousActivity >= delaySincePreviousActivity()
      ) {
        showNotification(project)
        setNotificationAlreadyShown()
      }
    }

    this.previousActivityTime = currentActivityTime
  }

  private fun isNotificationAlreadyShown(): Boolean {
    var notificationAlreadyShown = notificationAlreadyShown
    if (notificationAlreadyShown != null) return notificationAlreadyShown
    notificationAlreadyShown = PropertiesComponent.getInstance().getBoolean(WAYLAND_NOTIFICATION_ALREADY_SHOWN, false)
    this.notificationAlreadyShown = notificationAlreadyShown
    return notificationAlreadyShown
  }

  private fun setNotificationAlreadyShown() {
    this.notificationAlreadyShown = true
    PropertiesComponent.getInstance().setValue(WAYLAND_NOTIFICATION_ALREADY_SHOWN, true)
  }
}

private fun delaySinceFirstActivity(): Duration = Registry.intValue("wayland.notification.delay.after.first.activity", defaultValue = 20).minutes

private fun delaySincePreviousActivity(): Duration = Registry.intValue("wayland.notification.delay.after.previous.activity", defaultValue = 1).minutes

private fun showNotification(project: Project?) {
  Notification(
    "Wayland",
    IdeBundle.message("notification.wayland", "https://jetbrains.com/TODO-blog-post"), // TODO
    NotificationType.INFORMATION
  ).also {
    it.isSuggestionType = true
    it.isImportantSuggestion = true
  }.notify(project)
}

private const val WAYLAND_NOTIFICATION_ALREADY_SHOWN = "WAYLAND_NOTIFICATION_ALREADY_SHOWN"
