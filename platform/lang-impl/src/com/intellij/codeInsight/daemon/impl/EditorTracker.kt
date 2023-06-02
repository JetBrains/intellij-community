// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.ProjectFrameHelper.Companion.getFrameHelper
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.SmartList
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.beans.PropertyChangeListener
import javax.swing.SwingUtilities

private val LOG = logger<EditorTracker>()

private fun windowByEditor(editor: Editor, project: Project): Window? {
  val window = SwingUtilities.windowForComponent(editor.component)
  val frameHelper = getFrameHelper(window)
  return if (frameHelper != null && frameHelper.project !== project) null else window
}

open class EditorTracker(@JvmField protected val project: Project) : Disposable {
  private val windowToEditorsMap = HashMap<Window, MutableList<Editor>>()
  private val windowToWindowFocusListenerMap = HashMap<Window, WindowAdapter>()
  private val editorToWindowMap = HashMap<Editor, Window>()

  // accessed in EDT only
  private var myActiveEditors = emptyList<Editor>()
  private var activeWindow: Window? = null
  private val executeOnEditorRelease = HashMap<Editor, () -> Unit>()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): EditorTracker = project.service<EditorTracker>()
  }

  init {
    @Suppress("LeakingThis")
    (EditorFactory.getInstance().eventMulticaster as EditorEventMulticasterEx).addFocusChangeListener(object : FocusChangeListener {
      override fun focusGained(editor: Editor) {
        val window = editorToWindowMap.get(editor) ?: return
        val list = windowToEditorsMap.get(window)!!
        val index = list.indexOf(editor)
        LOG.assertTrue(index >= 0)
        if (list.isEmpty()) {
          return
        }

        if (list.size > 1) {
          for (i in index - 1 downTo 0) {
            list.set(i + 1, list.get(i))
          }
          list.set(0, editor)
        }
        setActiveWindow(window)
      }
    }, this)
  }

  internal class MyAppLevelFileEditorManagerListener : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
      val project = event.manager.project
      val frame = WindowManager.getInstance().getFrame(project)
      if (frame != null && frame.focusOwner != null) {
        getInstance(project).setActiveWindow(frame)
      }
    }
  }

  internal class MyAppLevelEditorFactoryListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
      val project = event.editor.project
      if (project != null && !project.isDisposed) {
        getInstance(project).editorCreated(event, project)
      }
    }

    override fun editorReleased(event: EditorFactoryEvent) {
      val project = event.editor.project
      if (project != null && !project.isDisposed) {
        getInstance(project).editorReleased(event, project)
      }
    }
  }

  private fun registerEditor(editor: Editor, project: Project) {
    unregisterEditor(editor)
    val window = windowByEditor(editor, project) ?: return
    editorToWindowMap.put(editor, window)
    var list = windowToEditorsMap.get(window)
    if (list == null) {
      list = ArrayList()
      windowToEditorsMap.put(window, list)
      if (window !is IdeFrameImpl) {
        val listener: WindowAdapter = object : WindowAdapter() {
          override fun windowGainedFocus(e: WindowEvent) {
            LOG.debug { "windowGainedFocus:$window" }
            setActiveWindow(window)
          }

          override fun windowLostFocus(e: WindowEvent) {
            LOG.debug { "windowLostFocus:$window" }
            setActiveWindow(null)
          }

          override fun windowClosed(event: WindowEvent) {
            LOG.debug { "windowClosed:$window" }
            setActiveWindow(null)
          }
        }
        windowToWindowFocusListenerMap.put(window, listener)
        window.addWindowFocusListener(listener)
        window.addWindowListener(listener)
        if (window.isFocused) {
          // windowGainedFocus is missed; activate by force
          setActiveWindow(window)
        }
      }
    }
    list.add(editor)
    if (activeWindow === window) {
      // to fire event
      setActiveWindow(window)
    }
  }

  private fun unregisterEditor(editor: Editor) {
    val oldWindow = editorToWindowMap.get(editor) ?: return
    editorToWindowMap.remove(editor)
    val editorList = windowToEditorsMap.get(oldWindow)!!
    val removed = editorList.remove(editor)
    LOG.assertTrue(removed)
    if (oldWindow === activeWindow) {
      updateActiveEditors(activeWindow)
    }
    if (editorList.isEmpty()) {
      windowToEditorsMap.remove(oldWindow)
      val listener = windowToWindowFocusListenerMap.remove(oldWindow)
      if (listener != null) {
        oldWindow.removeWindowFocusListener(listener)
        oldWindow.removeWindowListener(listener)
      }
    }
  }

  open var activeEditors: List<Editor>
    get() {
      ApplicationManager.getApplication().assertIsDispatchThread()
      return myActiveEditors
    }
    set(editors) {
      ApplicationManager.getApplication().assertIsDispatchThread()
      if (editors == myActiveEditors) {
        return
      }

      myActiveEditors = editors
      if (LOG.isDebugEnabled) {
        LOG.debug("active editors changed:")
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        for (editor in editors) {
          val psiFile = psiDocumentManager.getPsiFile(editor.document)
          LOG.debug("    $psiFile")
        }
      }
      project.messageBus.syncPublisher(EditorTrackerListener.TOPIC).activeEditorsChanged(editors)
    }

  private fun setActiveWindow(window: Window?) {
    activeWindow = window
    updateActiveEditors(window)
  }

  private fun updateActiveEditors(window: Window?) {
    val list = if (window == null) null else windowToEditorsMap.get(window)
    if (list.isNullOrEmpty()) {
      activeEditors = emptyList()
    }
    else {
      val editors = SmartList<Editor>()
      for (editor in list) {
        if (editor.contentComponent.isShowing && !editor.isDisposed) {
          editors.add(editor)
        }
      }
      activeEditors = editors
    }
  }

  private fun editorCreated(event: EditorFactoryEvent, project: Project) {
    val editor = event.editor
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    if (psiFile != null) {
      createEditorImpl(editor = editor, project = project)
    }
  }

  protected open fun createEditorImpl(editor: Editor, project: Project) {
    val component = editor.component
    val propertyChangeListener = PropertyChangeListener { event ->
      if (event.oldValue == null && event.newValue != null) {
        registerEditor(editor, project)
      }
    }
    component.addPropertyChangeListener("ancestor", propertyChangeListener)
    executeOnEditorRelease.put(editor) {
      component.removePropertyChangeListener("ancestor", propertyChangeListener)
    }
  }

  private fun editorReleased(event: EditorFactoryEvent, project: Project) {
    editorReleasedImpl(editor = event.editor, project = project)
  }

  protected open fun editorReleasedImpl(editor: Editor, project: Project) {
    unregisterEditor(editor)
    executeOnRelease(editor)
  }

  override fun dispose() {
    executeOnRelease(null)
  }

  private fun executeOnRelease(editor: Editor?) {
    if (editor == null) {
      for (r in executeOnEditorRelease.values) {
        r()
      }
      executeOnEditorRelease.clear()
    }
    else {
      executeOnEditorRelease.remove(editor)?.invoke()
    }
  }

  override fun toString(): String {
    return "EditorTracker(activeWindow=$activeWindow, activeEditors=$activeEditors, windowToEditorsMap=$windowToEditorsMap)"
  }
}
