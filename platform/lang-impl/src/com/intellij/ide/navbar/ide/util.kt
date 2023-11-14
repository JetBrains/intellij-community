// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NavBarIdeUtil")

package com.intellij.ide.navbar.ide

import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.VirtualFileAppearanceListener
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.problems.ProblemListener
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.Window
import java.awt.event.MouseEvent
import javax.swing.JComponent
import kotlin.coroutines.resume

internal val LOG: Logger = Logger.getInstance("#com.intellij.ide.navbar.ide")

internal fun UISettings.isNavbarShown(): Boolean {
  return showNavigationBar && !presentationMode
}

internal fun activityFlow(project: Project): Flow<Unit> {
  return channelFlow {
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

    val connection = project.messageBus.connect(this)
    connection.subscribe(FileStatusListener.TOPIC, object : FileStatusListener {
      override fun fileStatusesChanged() = fire()
      override fun fileStatusChanged(virtualFile: VirtualFile) = fire()
    })
    connection.subscribe(ProblemListener.TOPIC, object : ProblemListener {
      override fun problemsAppeared(file: VirtualFile) = fire()
      override fun problemsDisappeared(file: VirtualFile) = fire()
    })
    connection.subscribe(VirtualFileAppearanceListener.TOPIC, VirtualFileAppearanceListener {
      fire()
    })
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) = fire()
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) = fire()
      override fun selectionChanged(event: FileEditorManagerEvent) = fire()
    })

    fire()

    awaitClose()
  }.buffer(Channel.CONFLATED)
}

private fun skipActivityEvent(e: AWTEvent): Boolean {
  return e is MouseEvent && (e.id == MouseEvent.MOUSE_PRESSED || e.id == MouseEvent.MOUSE_RELEASED)
}

/**
 * This method assumes that [window] is an ancestor of [panel].
 *
 * @return data context of the focused component in the [window] of the [panel],
 * or `null` if [panel] has focus in hierarchy, or if the [window] of the [panel] is not focused
 */
internal suspend fun dataContext(window: Window, panel: JComponent): DataContext? = suspendCancellableCoroutine {
  IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(Runnable {
    it.resume(dataContextInner(window, panel))
  }, it.context.contextModality() ?: ModalityState.nonModal())
}

private fun dataContextInner(window: Window, panel: JComponent): DataContext? {
  if (!window.isFocused) {
    // IDEA-307406, IDEA-304798 Skip event when a window is out of focus (user is in a popup)
    return null
  }
  val focusedComponentInWindow = WindowManager.getInstance().getFocusedComponent(window)
                                 ?: return null
  if (UIUtil.isDescendingFrom(focusedComponentInWindow, panel)) {
    // ignore updates while panel or one of its children has focus
    return null
  }
  val dataContext = DataManager.getInstance().getDataContext(focusedComponentInWindow)
  val uiSnapshot = Utils.createAsyncDataContext(dataContext)
  return AnActionEvent.getInjectedDataContext(uiSnapshot)
}
