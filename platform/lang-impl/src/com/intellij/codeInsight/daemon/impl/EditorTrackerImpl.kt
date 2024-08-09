// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.writeIntentReadAction
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
import com.intellij.openapi.wm.impl.ProjectFrameHelper
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.beans.PropertyChangeListener
import javax.swing.SwingUtilities

open class EditorTrackerImpl(@JvmField protected val project: Project) : EditorTracker, Disposable {
  private val windowToEditorsMap = HashMap<Window, MutableList<Editor>>()
  private val windowToWindowFocusListenerMap = HashMap<Window, WindowAdapter>()
  private val editorToWindowMap = HashMap<Editor, Window>()

  private var activeWindow: Window? = null
  private val executeOnEditorRelease = HashMap<Editor, () -> Unit>()

  companion object {
    fun getInstance(project: Project): EditorTrackerImpl = EditorTracker.getInstance(project) as EditorTrackerImpl

    private val LOG = logger<EditorTracker>()

    private fun windowByEditor(editor: Editor, project: Project): Window? {
      val window = SwingUtilities.windowForComponent(editor.component)
      val frameHelper = ProjectFrameHelper.getFrameHelper(window)
      return if (frameHelper != null && frameHelper.project !== project) null else window
    }
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
        WriteIntentReadAction.run {
          setActiveWindow(window)
        }
      }
    }, this)
  }

  internal class MyAppLevelFileEditorManagerListener : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
      val project = event.manager.project
      val window = WindowManager.getInstance().getFrame(project) ?: return
      val editorTracker = getInstance(project)
      if (editorTracker.activeWindow === window) {
        LOG.debug { "Skip `setActiveWindow` calling on `FileEditorManagerListener.selectionChanged` (reason=same window, window=$window)" }
      }
      else {
        editorTracker.setActiveWindow(window)
      }
    }
  }

  internal class MyAppLevelEditorFactoryListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
      val editor = event.editor
      val project = editor.project?.takeIf { !it.isDisposed } ?: return
      getInstance(project).createEditorImpl(editor, project)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
      val editor = event.editor
      val project = editor.project?.takeIf { !it.isDisposed } ?: return
      getInstance(project).editorReleasedImpl(editor, project)
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

  @get:RequiresEdt
  @set:RequiresEdt
  override var activeEditors: List<Editor> = emptyList()
    set(editors) {
      if (editors == field) {
        return
      }

      field = editors
      if (!project.isDisposed) {
        if (LOG.isDebugEnabled) {
          LOG.debug("active editors changed: " + editors.joinToString(separator = "\n    ") {
            WriteIntentReadAction.compute<String> {
              PsiDocumentManager.getInstance(project).getPsiFile(it.document).toString()
            }
          })

        }
        //maybe readaction
        WriteIntentReadAction.run {
          project.messageBus.syncPublisher(EditorTrackerListener.TOPIC).activeEditorsChanged(editors)
        }
      }
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
      activeEditors = list.filter { !it.isDisposed && it.contentComponent.isShowing }
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
