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
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.CoroutineScope
import java.awt.BorderLayout
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JScrollPane
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
    val updateAllEditorsListener = object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        updateAllEditors()
      }
    }
    Registry.get(MinimapRegistry.MODE_KEY).addListener(updateAllEditorsListener, this)
    // Let file-type policies declare additional registry keys they depend on.
    for (key in MinimapFileSupportPolicy.EP_NAME.extensionList.flatMap { it.getWatchedRegistryKeys() }) {
      Registry.get(key).addListener(updateAllEditorsListener, this)
    }
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
    if (!editorImpl.contentComponent.isShowing) return false

    val project = editorImpl.project ?: return false
    val document = editorImpl.document
    val virtualFile = PsiDocumentManager.getInstance(project).getPsiFile(document)?.virtualFile
                      ?: FileDocumentManager.getInstance().getFile(document)
                      ?: return false

    val supportLevel = MinimapFileSupportPolicy.forFileType(virtualFile.fileType)
    if (!settings.state.enabled) return false

    // INDEPENDENT bypasses the global mode and IDE-availability checks, but still obeys
    // user-facing visibility settings and the context-menu disable action.
    if (supportLevel == MinimapSupportLevel.INDEPENDENT) return true

    if (!MinimapRegistry.isEnabled()) return false
    return supportLevel != MinimapSupportLevel.UNSUPPORTED
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

    editor.putUserData(MINI_MAP_SCROLLBAR_STATE_KEY, MinimapScrollbarState(
      scrollPane = scrollPane,
      originalLayout = originalLayout,
      originalViewportBorder = originalViewportBorder,
    ))

    // Add minimap as a direct child of the scroll pane; the custom layout positions it
    // between the viewport and the vertical scrollbar. The scrollbar is never moved,
    // keeping PanelWithFloatingToolbar.doLayout() and the inspection toolbar working correctly.
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
      editor.putUserData(MINI_MAP_SCROLLBAR_STATE_KEY, null)
    }

    editor.putUserData(MINI_MAP_WRAPPER_KEY, null)
    editor.putUserData(MINI_MAP_PANEL_KEY, null)
    panelsToClose.forEach { it.onClose() }
  }

  private fun oppositeSide(where: String): String {
    return if (where == BorderLayout.LINE_END) BorderLayout.LINE_START else BorderLayout.LINE_END
  }

  private data class MinimapScrollbarState(
    val scrollPane: JScrollPane,
    val originalLayout: ScrollPaneLayout?,
    val originalViewportBorder: Border?,
  )

  companion object {
    fun getInstance(): MinimapService = service<MinimapService>()
    private val MINI_MAP_PANEL_KEY: Key<MinimapPanel> = Key.create("com.intellij.ide.minimap.panel")
    private val MINI_MAP_VISIBILITY_LISTENER_KEY: Key<HierarchyListener> = Key.create("com.intellij.ide.minimap.visibility.listener")
    private val MINI_MAP_SCROLLBAR_STATE_KEY: Key<MinimapScrollbarState> = Key.create("com.intellij.ide.minimap.scrollbar.state")
    private val MINI_MAP_WRAPPER_KEY: Key<JPanel> = Key.create("com.intellij.ide.minimap.wrapper")
  }
}
