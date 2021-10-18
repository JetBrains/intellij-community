// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.documentation.ToggleShowDocsOnHoverAction
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.gotoByName.ChooseByNameBase
import com.intellij.lang.documentation.ide.actions.AdjustFontSizeAction
import com.intellij.lang.documentation.ide.actions.TOGGLE_SHOW_IN_POPUP_ACTION_ID
import com.intellij.lang.documentation.ide.actions.navigationActions
import com.intellij.lang.documentation.ide.ui.DocumentationToolWindowUI
import com.intellij.lang.documentation.ide.ui.DocumentationUI
import com.intellij.lang.documentation.ide.ui.toolWindowUI
import com.intellij.lang.documentation.impl.DocumentationRequest
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.util.ui.EDT
import javax.swing.JPanel

@Service
internal class DocumentationToolWindowManager(private val project: Project) {

  companion object {

    const val TOOL_WINDOW_ID: String = "documentation.v2"

    fun instance(project: Project): DocumentationToolWindowManager = project.service()
  }

  private val toolWindow: ToolWindowEx = ToolWindowManager.getInstance(project).registerToolWindow(RegisterToolWindowTask.closableSecondary(
    id = TOOL_WINDOW_ID,
    stripeTitle = IdeBundle.messagePointer("toolwindow.stripe.documentation.v2"),
    icon = AllIcons.Toolwindows.Documentation,
    anchor = ToolWindowAnchor.RIGHT,
  )) as ToolWindowEx
  private val contentManager: ContentManager = toolWindow.contentManager

  init {
    toolWindow.setAdditionalGearActions(DefaultActionGroup(
      ActionManager.getInstance().getAction(TOGGLE_SHOW_IN_POPUP_ACTION_ID),
      ToggleShowDocsOnHoverAction(),
      AdjustFontSizeAction(), // TODO this action doesn't work because of wrong DataContext
    ))
    if (Registry.`is`("documentation.v2.tw.navigation.actions")) {
      // TODO these actions are always visible, but they are unavailable in the tool window title,
      //  because they are updated with the wrong DataContext.
      toolWindow.setTitleActions(navigationActions())
    }
    toolWindow.installWatcher(contentManager)
    toolWindow.component.putClientProperty(ChooseByNameBase.TEMPORARILY_FOCUSABLE_COMPONENT_KEY, true)
    toolWindow.helpId = "reference.toolWindows.Documentation"
  }

  /**
   * @return `true` if a preview tab is visible, `false` if no preview exists, or if a preview is hidden
   */
  fun hasVisiblePreview(): Boolean {
    return getVisiblePreviewContent() != null
  }

  /**
   * @return `true` if a preview tab is visible, `false` if no preview exists, or if a preview is hidden
   */
  fun focusVisiblePreview(): Boolean {
    val content = getVisiblePreviewContent()?.content ?: return false
    contentManager.requestFocus(content, false)
    return true
  }

  /**
   * Orders existing visible preview tab to display [request].
   *
   * @return `true` if a preview tab is visible, `false` if no preview exists, or if a preview is hidden
   */
  fun updateVisiblePreview(request: DocumentationRequest): Boolean {
    val previewContent = getVisiblePreviewContent() ?: return false
    previewContent.toolWindowUI.browser.resetBrowser(request)
    return true
  }

  /**
   * Creates a new preview tab if no preview tab exists,
   * orders it to display [request],
   * and makes it visible.
   */
  fun previewInToolWindow(request: DocumentationRequest) {
    val previewContent = getPreviewContent()
    if (previewContent == null) {
      val browser = DocumentationBrowser.createBrowser(project, initialRequest = request)
      previewInToolWindow(DocumentationUI(project, browser), addNewContent())
    }
    else {
      previewContent.toolWindowUI.browser.resetBrowser(request)
      makeVisible(previewContent.content)
    }
  }

  /**
   * Creates a new preview tab, or replaces existing tab content, and displays [ui] in it.
   * The [ui] will be disposed once the tab is closed.
   */
  fun previewInToolWindow(ui: DocumentationUI) {
    EDT.assertIsEdt()
    val previewContent = getPreviewContent()
    val content = if (previewContent == null) {
      addNewContent()
    }
    else {
      Disposer.dispose(previewContent.toolWindowUI)
      previewContent.content
    }
    previewInToolWindow(ui, content)
  }

  private fun previewInToolWindow(ui: DocumentationUI, content: Content) {
    val newUI = DocumentationToolWindowUI(project, ui, content)
    content.component = newUI.contentComponent
    makeVisible(content)
  }

  private fun makeVisible(content: Content) {
    contentManager.setSelectedContent(content)
    toolWindow.show()
  }

  private data class PreviewContent(
    val toolWindowUI: DocumentationToolWindowUI,
    val content: Content,
  )

  private fun getVisiblePreviewContent(): PreviewContent? {
    if (!toolWindow.isVisible) {
      return null
    }
    val selectedContent = contentManager.selectedContent ?: return null
    val toolWindowUI = selectedContent.toolWindowUI ?: return null
    return PreviewContent(toolWindowUI, selectedContent)
  }

  private fun getPreviewContent(): PreviewContent? {
    for (content in contentManager.contents) {
      val toolWindowUI = content.toolWindowUI ?: continue
      return PreviewContent(toolWindowUI, content)
    }
    return null
  }

  private fun addNewContent(): Content {
    val content = ContentFactory.SERVICE.getInstance().createContent(JPanel(), null, false).also {
      it.isCloseable = true
      it.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
    }
    contentManager.addContent(content)
    return content
  }
}
