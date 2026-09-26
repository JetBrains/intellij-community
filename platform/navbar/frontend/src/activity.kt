// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.channels.ProducerScope
import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.event.MouseEvent

fun ProducerScope<Unit>.fireOnIdeActivity(project: Project) {
  // Just a Unit-returning shortcut
  fun fire() {
    trySend(Unit)
  }

  IdeEventQueue.getInstance().addActivityListener(Runnable {
    val currentEvent = EventQueue.getCurrentEvent() ?: return@Runnable
    if (!skipActivityEvent(currentEvent)) {
      fire()
    }
  }, this)

  project.messageBus
    .connect(this)
    .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) = fire()
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) = fire()
      override fun selectionChanged(event: FileEditorManagerEvent) = fire()
    })
}

private fun skipActivityEvent(e: AWTEvent): Boolean {
  return e is MouseEvent && (e.id == MouseEvent.MOUSE_PRESSED || e.id == MouseEvent.MOUSE_RELEASED)
}
