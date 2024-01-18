// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import java.awt.BorderLayout
import javax.swing.JPanel

@Service
class MinimapService : Disposable {

  companion object {
    fun getInstance(): MinimapService = service<MinimapService>()
    private val MINI_MAP_PANEL_KEY: Key<MinimapPanel> = Key.create("com.intellij.ide.minimap.panel")
  }

  private val settings = MinimapSettings.getInstance()

  private val onSettingsChange = { type: MinimapSettings.SettingsChangeType ->
    if (type == MinimapSettings.SettingsChangeType.WithUiRebuild) {
      updateAllEditors()
    }
  }

  init {
    MinimapSettings.getInstance().settingsChangeCallback += onSettingsChange
  }

  fun updateAllEditors() {
    EditorFactory.getInstance().allEditors.forEach { editor ->
      getEditorImpl(editor)?.let {
        removeMinimap(it)
        if (settings.state.enabled) {
          addMinimap(it)
        }
      }
    }
  }

  override fun dispose() {
    MinimapSettings.getInstance().settingsChangeCallback -= onSettingsChange
  }

  private fun getEditorImpl(editor: Editor): EditorImpl? {
    val editorImpl = editor as? EditorImpl ?: return null
    if (editorImpl.editorKind != EditorKind.MAIN_EDITOR) return null
    val virtualFile = editorImpl.virtualFile ?: FileDocumentManager.getInstance().getFile(editor.document) ?: return null
    if (settings.state.fileTypes.isNotEmpty() && !settings.state.fileTypes.contains(virtualFile.fileType.defaultExtension)) return null
    return editorImpl
  }

  fun editorOpened(editor: Editor) {
    if (!settings.state.enabled) {
      return
    }
    getEditorImpl(editor)?.let { addMinimap(it) }
  }

  private fun getPanel(fileEditor: EditorImpl): JPanel? {
    return fileEditor.component as? JPanel
  }

  private fun addMinimap(textEditor: EditorImpl) {
    val panel = getPanel(textEditor) ?: return

    val where = if (settings.state.rightAligned) BorderLayout.LINE_END else BorderLayout.LINE_START

    if ((panel.layout as? BorderLayout)?.getLayoutComponent(where) == null) {
      val minimapPanel = MinimapPanel(textEditor.disposable, textEditor, panel)
      panel.add(minimapPanel, where)
      textEditor.putUserData(MINI_MAP_PANEL_KEY, minimapPanel)

      Disposer.register(textEditor.disposable) {
        textEditor.getUserData(MINI_MAP_PANEL_KEY)?.onClose()
        textEditor.putUserData(MINI_MAP_PANEL_KEY, null)
      }
      panel.revalidate()
      panel.repaint()
    }
  }

  private fun removeMinimap(editor: EditorImpl) {
    val minimapPanel = editor.getUserData(MINI_MAP_PANEL_KEY) ?: return
    minimapPanel.onClose()
    editor.putUserData(MINI_MAP_PANEL_KEY, null)

    minimapPanel.parent?.apply {
      remove(minimapPanel)
      revalidate()
      repaint()
    }
  }
}