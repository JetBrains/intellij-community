// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.miniMap

import com.intellij.ide.miniMap.settings.MiniMapSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import java.awt.BorderLayout
import javax.swing.JPanel

class MiniMapService : Disposable {

  companion object {
    fun getInstance() = service<MiniMapService>()
    private val MINI_MAP_PANEL_KEY: Key<MiniMapPanel> = Key.create("com.intellij.ide.minimap.panel")
  }

  private val settings = MiniMapSettings.getInstance()
  private var state = settings.state

  private val onSettingsChange = { type: MiniMapSettings.SettingsChangeType ->
    if (type == MiniMapSettings.SettingsChangeType.WithUiRebuild) {
      updateAllEditors()
    }
  }

  init {
    MiniMapSettings.getInstance().settingsChangeCallback += onSettingsChange
  }

  fun updateAllEditors() {
    EditorFactory.getInstance().allEditors.forEach { editor ->
      getEditorImpl(editor)?.let {
        removeMiniMap(it)
        if (settings.state.enabled) {
          addMiniMap(it)
        }
      }
    }
  }

  override fun dispose() {
    MiniMapSettings.getInstance().settingsChangeCallback -= onSettingsChange
  }

  private fun getEditorImpl(editor: Editor): EditorImpl? {
    val editorImpl = editor as? EditorImpl ?: return null
    val virtualFile = editorImpl.virtualFile ?: FileDocumentManager.getInstance().getFile(editor.document) ?: return null
    if (settings.state.fileTypes.isNotEmpty() && !settings.state.fileTypes.contains(virtualFile.fileType.defaultExtension)) return null
    return editorImpl
  }

  fun editorOpened(editor: Editor) {
    if (!settings.state.enabled) {
      return
    }
    getEditorImpl(editor)?.let { addMiniMap(it) }
  }

  private fun getPanel(fileEditor: EditorImpl): JPanel? {
    return fileEditor.component as? JPanel
  }

  private fun addMiniMap(textEditor: EditorImpl) {
    val panel = getPanel(textEditor) ?: return

    val where = if (state.rightAligned) BorderLayout.LINE_END else BorderLayout.LINE_START

    if ((panel.layout as? BorderLayout)?.getLayoutComponent(where) == null) {
      val miniMapPanel = MiniMapPanel(textEditor.disposable, textEditor, panel)
      panel.add(miniMapPanel, where)
      textEditor.putUserData(MINI_MAP_PANEL_KEY, miniMapPanel)

      Disposer.register(textEditor.disposable) {
        textEditor.getUserData(MINI_MAP_PANEL_KEY)?.onClose()
        textEditor.putUserData(MINI_MAP_PANEL_KEY, null)
      }
      panel.revalidate()
      panel.repaint()
    }
  }

  private fun removeMiniMap(editor: EditorImpl) {
    val miniMapPanel = editor.getUserData(MINI_MAP_PANEL_KEY) ?: return
    miniMapPanel.onClose()
    editor.putUserData(MINI_MAP_PANEL_KEY, null)

    miniMapPanel.parent?.apply {
      remove(miniMapPanel)
      revalidate()
      repaint()
    }
  }
}