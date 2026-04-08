// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.model.MinimapFileSupportPolicy
import com.intellij.ide.minimap.model.MinimapSupportLevel
import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.CoroutineScope
import java.awt.BorderLayout
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import javax.swing.JPanel

@Service(Service.Level.APP)
class MinimapService(private val scope: CoroutineScope) : Disposable {
  private val settings = MinimapSettings.getInstance()

  private val onSettingsChange = { type: MinimapSettings.SettingsChangeType ->
    if (type == MinimapSettings.SettingsChangeType.WithUiRebuild) {
      updateAllEditors()
    }
  }

  init {
    MinimapSettings.getInstance().settingsChangeCallback += onSettingsChange
  }

  override fun dispose() {
    MinimapSettings.getInstance().settingsChangeCallback -= onSettingsChange
  }

  fun editorOpened(editor: Editor) {
    val editorImpl = getMainEditorImpl(editor) ?: return
    installVisibilityListener(editorImpl)
    updateMinimap(editorImpl)
  }


  fun updateAllEditors() {
    EditorFactory.getInstance().allEditors.forEach { editor ->
      getMainEditorImpl(editor)?.let {
        installVisibilityListener(it)
        updateMinimap(it)
      }
    }
  }

  private fun getMainEditorImpl(editor: Editor): EditorImpl? {
    val editorImpl = editor as? EditorImpl ?: return null
    if (editorImpl.editorKind != EditorKind.MAIN_EDITOR) return null
    return editorImpl
  }

  private fun shouldHaveMinimap(editorImpl: EditorImpl): Boolean {
    if (!settings.state.enabled) return false
    if (!editorImpl.contentComponent.isShowing) return false

    val project = editorImpl.project ?: return false
    val document = editorImpl.document
    val virtualFile = PsiDocumentManager.getInstance(project).getPsiFile(document)?.virtualFile
                      ?: FileDocumentManager.getInstance().getFile(document)
                      ?: return false

    return MinimapFileSupportPolicy.forFileType(virtualFile.fileType) != MinimapSupportLevel.UNSUPPORTED
  }

  private fun updateMinimap(editorImpl: EditorImpl) {
    val shouldHave = shouldHaveMinimap(editorImpl)
    if (shouldHave) {
      addMinimap(editorImpl)
    }
    else {
      removeMinimap(editorImpl)
    }
  }

  private fun installVisibilityListener(editorImpl: EditorImpl) {
    if (editorImpl.getUserData(MINI_MAP_VISIBILITY_LISTENER_KEY) != null) return

    val listener = HierarchyListener { event ->
      if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() == 0L) return@HierarchyListener
      if (editorImpl.isDisposed) return@HierarchyListener
      // Avoid mutating the component hierarchy while it is being hidden/closed.
      if (!editorImpl.contentComponent.isShowing) return@HierarchyListener
      updateMinimap(editorImpl)
    }

    editorImpl.contentComponent.addHierarchyListener(listener)
    editorImpl.putUserData(MINI_MAP_VISIBILITY_LISTENER_KEY, listener)
    EditorUtil.disposeWithEditor(editorImpl, Disposable {
      editorImpl.contentComponent.removeHierarchyListener(listener)
      editorImpl.putUserData(MINI_MAP_VISIBILITY_LISTENER_KEY, null)
    })
  }

  private fun getPanel(fileEditor: EditorImpl): JPanel? {
    return fileEditor.component as? JPanel
  }

  private fun addMinimap(textEditor: EditorImpl) {
    val panel = getPanel(textEditor) ?: return

    val where = if (settings.state.rightAligned) BorderLayout.LINE_END else BorderLayout.LINE_START

    val borderLayout = panel.layout as? BorderLayout ?: return
    val existingAtRequestedSide = borderLayout.getLayoutComponent(where) as? MinimapPanel
    val existingAtOppositeSide = borderLayout.getLayoutComponent(oppositeSide(where)) as? MinimapPanel
    val existingFromUserData = textEditor.getUserData(MINI_MAP_PANEL_KEY)
    if (existingAtRequestedSide != null && existingAtOppositeSide == null && (existingFromUserData == null || existingFromUserData === existingAtRequestedSide)) {
      textEditor.putUserData(MINI_MAP_PANEL_KEY, existingAtRequestedSide)
      return
    }

    cleanupMinimapPanels(textEditor, panel)

    val minimapPanel = MinimapPanel(scope, textEditor, panel)

    panel.add(minimapPanel, where)
    textEditor.putUserData(MINI_MAP_PANEL_KEY, minimapPanel)

    panel.revalidate()
    panel.repaint()
  }

  private fun removeMinimap(editor: EditorImpl) {
    val panel = getPanel(editor)
    cleanupMinimapPanels(editor, panel)
  }

  private fun cleanupMinimapPanels(editor: EditorImpl, panel: JPanel?) {
    val panelsToClose = mutableSetOf<MinimapPanel>()
    editor.getUserData(MINI_MAP_PANEL_KEY)?.let { panelsToClose.add(it) }
    panel?.components?.filterIsInstance<MinimapPanel>()?.forEach { panelsToClose.add(it) }

    editor.putUserData(MINI_MAP_PANEL_KEY, null)
    panelsToClose.forEach { it.onClose() }
  }

  private fun oppositeSide(where: String): String {
    return if (where == BorderLayout.LINE_END) BorderLayout.LINE_START else BorderLayout.LINE_END
  }

  companion object {
    fun getInstance(): MinimapService = service<MinimapService>()
    private val MINI_MAP_PANEL_KEY: Key<MinimapPanel> = Key.create("com.intellij.ide.minimap.panel")
    private val MINI_MAP_VISIBILITY_LISTENER_KEY: Key<HierarchyListener> = Key.create("com.intellij.ide.minimap.visibility.listener")
  }
}
