// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NavBarIdeUtil")

package com.intellij.ide.navbar.ide

import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.VirtualFileAppearanceListener
import com.intellij.openapi.Disposable
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
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FileStatusListener
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.problems.ProblemListener
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.awt.AWTEvent
import java.awt.Window
import java.awt.event.MouseEvent
import kotlin.coroutines.resume

internal val isNavbarV2Enabled: Boolean = Registry.`is`("ide.navBar.v2", false)

internal val LOG: Logger = Logger.getInstance("#com.intellij.ide.navbar.ide")

internal fun UISettings.isNavbarShown(): Boolean {
  return showNavigationBar && !presentationMode
}

internal fun activityFlow(project: Project): Flow<Unit> {
  return channelFlow {
    val disposable: Disposable = Disposer.newDisposable()

    // Just a Unit-returning shortcut
    fun fire() {
      trySend(Unit)
    }

    IdeEventQueue.getInstance().addActivityListener(Runnable {
      val currentEvent = IdeEventQueue.getCurrentEvent() ?: return@Runnable
      if (!skipActivityEvent(currentEvent, project)) {
        fire()
      }
    }, disposable)

    FileStatusManager.getInstance(project).addFileStatusListener(object : FileStatusListener {
      override fun fileStatusesChanged() = fire()
      override fun fileStatusChanged(virtualFile: VirtualFile) = fire()
    }, disposable)

    val connection = project.messageBus.connect(this)
    connection.subscribe(ProblemListener.TOPIC, object : ProblemListener {
      override fun problemsAppeared(file: VirtualFile) = fire()
      override fun problemsDisappeared(file: VirtualFile) = fire()
    })
    connection.subscribe(VirtualFileAppearanceListener.TOPIC, VirtualFileAppearanceListener { fire() })

    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) = fire()
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) = fire()
      override fun selectionChanged(event: FileEditorManagerEvent) = fire()
    })

    fire()

    awaitClose {
      Disposer.dispose(disposable)
    }
  }.buffer(Channel.CONFLATED)
}

private fun skipActivityEvent(e: AWTEvent, project: Project): Boolean {

  val window: Window? = WindowManager.getInstance().getFrame(project)
  if (window != null && !window.isFocused) {
    // IDEA-307406, IDEA-304798 Skip event when window is out of focus (user is in a popup)
    return true
  }

  if (e is MouseEvent && (e.id == MouseEvent.MOUSE_PRESSED || e.id == MouseEvent.MOUSE_RELEASED)) {
    return true
  }
  return false
}

// TODO move to DataManager
internal suspend fun focusDataContext(): DataContext = suspendCancellableCoroutine {
  IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(Runnable {
    @Suppress("DEPRECATION")
    val dataContextFromFocusedComponent = DataManager.getInstance().dataContext
    val uiSnapshot = Utils.wrapToAsyncDataContext(dataContextFromFocusedComponent)
    val asyncDataContext = AnActionEvent.getInjectedDataContext(uiSnapshot)
    it.resume(asyncDataContext)
  }, it.context.contextModality() ?: ModalityState.NON_MODAL)
}
