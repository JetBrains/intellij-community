// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.model.MinimapFileSupportPolicy
import com.intellij.ide.minimap.model.MinimapSupportLevel
import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.CoroutineScope
import java.awt.BorderLayout
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.ScrollPaneLayout
import javax.swing.border.Border

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
    updateMinimap(editorImpl)
  }

  fun updateAllEditors() {
    EditorFactory.getInstance().allEditors.forEach { editor ->
      getMainEditorImpl(editor)?.let {
        updateMinimap(it)
      }
    }
  }

  fun repaintGutter(editor: Editor) {
    val editorImpl = getMainEditorImpl(editor) ?: return
    val minimapPanel = editorImpl.getUserData(MINI_MAP_PANEL_KEY)
                      ?: getPanel(editorImpl)?.components?.filterIsInstance<MinimapPanel>()?.firstOrNull()
                      ?: return
    minimapPanel.repaintGutter()
  }

  fun repaint(editor: Editor) {
    val editorImpl = getMainEditorImpl(editor) ?: return
    val minimapPanel = editorImpl.getUserData(MINI_MAP_PANEL_KEY)
                      ?: getPanel(editorImpl)?.components?.filterIsInstance<MinimapPanel>()?.firstOrNull()
                      ?: return
    minimapPanel.repaint()
  }

  fun refresh(editor: Editor) {
    val editorImpl = getMainEditorImpl(editor) ?: return
    val minimapPanel = editorImpl.getUserData(MINI_MAP_PANEL_KEY)
                      ?: getPanel(editorImpl)?.components?.filterIsInstance<MinimapPanel>()?.firstOrNull()
                      ?: return
    minimapPanel.refreshSnapshot()
    minimapPanel.repaint()
  }

  private fun getMainEditorImpl(editor: Editor): EditorImpl? {
    val editorImpl = editor as? EditorImpl ?: return null
    if (editorImpl.editorKind != EditorKind.MAIN_EDITOR) return null
    return editorImpl
  }

  private fun shouldHaveMinimap(editorImpl: EditorImpl): Boolean {
    if (!MinimapAvailability.isAvailable()) return false
    if (!settings.state.enabled) return false

    val virtualFile = getEditorVirtualFile(editorImpl)
                      ?: return false

    val supportLevel = MinimapFileSupportPolicy.forFileType(virtualFile.fileType)
    return supportLevel != MinimapSupportLevel.UNSUPPORTED
  }

  private fun getEditorVirtualFile(editorImpl: EditorImpl): VirtualFile? {
    val project = editorImpl.project ?: return FileDocumentManager.getInstance().getFile(editorImpl.document)
    val document = editorImpl.document
    return WriteIntentReadAction.compute<VirtualFile?> {
      PsiDocumentManager.getInstance(project).getPsiFile(document)?.virtualFile
    } ?: FileDocumentManager.getInstance().getFile(document)
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

  private fun getPanel(fileEditor: EditorImpl): JPanel? {
    return fileEditor.component as? JPanel
  }

  private fun addMinimap(textEditor: EditorImpl) {
    val panel = getPanel(textEditor) ?: return
    val borderLayout = panel.layout as? BorderLayout ?: return
    val insideScrollbarMode = settings.state.insideScrollbar && settings.state.rightAligned

    // Skip recreation if the minimap is already in the correct position.
    if (insideScrollbarMode) {
      val state = textEditor.getUserData(MINI_MAP_SCROLLBAR_STATE_KEY)
      if (state != null && state.scrollPane.layout is MinimapScrollPaneLayout) return
    }
    else {
      val where = if (settings.state.rightAligned) BorderLayout.LINE_END else BorderLayout.LINE_START
      val existingAtRequestedSide = borderLayout.getLayoutComponent(where) as? MinimapPanel
      val existingAtOppositeSide = borderLayout.getLayoutComponent(oppositeSide(where)) as? MinimapPanel
      val existingFromUserData = textEditor.getUserData(MINI_MAP_PANEL_KEY)
      if (existingAtRequestedSide != null && existingAtOppositeSide == null &&
          (existingFromUserData == null || existingFromUserData === existingAtRequestedSide)) {
        textEditor.putUserData(MINI_MAP_PANEL_KEY, existingAtRequestedSide)
        return
      }
    }

    cleanupMinimapPanels(textEditor, panel)

    val minimapPanel = MinimapPanel(scope, textEditor, panel)

    if (insideScrollbarMode) {
      addMinimapInsideScrollbar(textEditor, panel, minimapPanel)
    }
    else {
      val where = if (settings.state.rightAligned) BorderLayout.LINE_END else BorderLayout.LINE_START
      panel.add(minimapPanel, where)
    }

    textEditor.putUserData(MINI_MAP_PANEL_KEY, minimapPanel)

    panel.revalidate()
    panel.repaint()
  }

  private fun addMinimapInsideScrollbar(editor: EditorImpl, panel: JPanel, minimapPanel: MinimapPanel) {
    val borderLayout = panel.layout as? BorderLayout ?: return
    val layeredPane = borderLayout.getLayoutComponent(BorderLayout.CENTER) as? JLayeredPane ?: return
    val scrollPane = layeredPane.components.filterIsInstance<JScrollPane>().firstOrNull() ?: return

    val originalLayout = scrollPane.layout as? ScrollPaneLayout
    val originalViewportBorder = scrollPane.viewportBorder
    val originalViewportScrollMode = scrollPane.viewport?.scrollMode
    val originalRowHeaderScrollMode = scrollPane.rowHeader?.scrollMode
    val originalColumnHeaderScrollMode = scrollPane.columnHeader?.scrollMode

    editor.putUserData(MINI_MAP_SCROLLBAR_STATE_KEY, MinimapScrollbarState(
      scrollPane = scrollPane,
      originalLayout = originalLayout,
      originalViewportBorder = originalViewportBorder,
      originalViewportScrollMode = originalViewportScrollMode,
      originalRowHeaderScrollMode = originalRowHeaderScrollMode,
      originalColumnHeaderScrollMode = originalColumnHeaderScrollMode,
    ))

    // Add minimap as a direct child of the scroll pane; the custom layout positions it
    // between the viewport and the vertical scrollbar. The scrollbar is never moved,
    // keeping PanelWithFloatingToolbar.doLayout() and the inspection toolbar working correctly.
    disableBlitScrolling(scrollPane)
    scrollPane.add(minimapPanel)
    scrollPane.layout = MinimapScrollPaneLayout(minimapPanel)
    scrollPane.viewportBorder = MinimapScrollPaneLayout.createViewportBorder(scrollPane, minimapPanel, originalViewportBorder)
  }

  private fun removeMinimap(editor: EditorImpl) {
    val panel = getPanel(editor)
    cleanupMinimapPanels(editor, panel)
  }

  private fun cleanupMinimapPanels(editor: EditorImpl, panel: JPanel?) {
    val panelsToClose = mutableSetOf<MinimapPanel>()
    editor.getUserData(MINI_MAP_PANEL_KEY)?.let { panelsToClose.add(it) }
    panel?.components?.filterIsInstance<MinimapPanel>()?.forEach { panelsToClose.add(it) }

    editor.getUserData(MINI_MAP_SCROLLBAR_STATE_KEY)?.let { state ->
      // Collect and remove minimap panels from inside the scroll pane
      state.scrollPane.components.filterIsInstance<MinimapPanel>().toList().forEach { mp ->
        panelsToClose.add(mp)
        state.scrollPane.remove(mp)
      }
      state.scrollPane.viewportBorder = state.originalViewportBorder
      state.originalLayout?.let { state.scrollPane.layout = it }
      restoreScrollMode(state.scrollPane.viewport, state.originalViewportScrollMode)
      restoreScrollMode(state.scrollPane.rowHeader, state.originalRowHeaderScrollMode)
      restoreScrollMode(state.scrollPane.columnHeader, state.originalColumnHeaderScrollMode)
      editor.putUserData(MINI_MAP_SCROLLBAR_STATE_KEY, null)
    }

    editor.putUserData(MINI_MAP_PANEL_KEY, null)
    panelsToClose.forEach { it.onClose() }
  }

  private fun oppositeSide(where: String): String {
    return if (where == BorderLayout.LINE_END) BorderLayout.LINE_START else BorderLayout.LINE_END
  }

  private fun disableBlitScrolling(scrollPane: JScrollPane) {
    // The embedded minimap changes scroll pane child geometry; blit scrolling can copy stale rounded-corner pixels into the editor/gutter.
    scrollPane.viewport?.scrollMode = JViewport.SIMPLE_SCROLL_MODE
    scrollPane.rowHeader?.scrollMode = JViewport.SIMPLE_SCROLL_MODE
    scrollPane.columnHeader?.scrollMode = JViewport.SIMPLE_SCROLL_MODE
  }

  private fun restoreScrollMode(viewport: JViewport?, scrollMode: Int?) {
    if (viewport != null && scrollMode != null) {
      viewport.scrollMode = scrollMode
    }
  }

  private data class MinimapScrollbarState(
    val scrollPane: JScrollPane,
    val originalLayout: ScrollPaneLayout?,
    val originalViewportBorder: Border?,
    val originalViewportScrollMode: Int?,
    val originalRowHeaderScrollMode: Int?,
    val originalColumnHeaderScrollMode: Int?,
  )

  companion object {
    fun getInstance(): MinimapService = service<MinimapService>()
    private val MINI_MAP_PANEL_KEY: Key<MinimapPanel> = Key.create("com.intellij.ide.minimap.panel")
    private val MINI_MAP_SCROLLBAR_STATE_KEY: Key<MinimapScrollbarState> = Key.create("com.intellij.ide.minimap.scrollbar.state")
  }
}
