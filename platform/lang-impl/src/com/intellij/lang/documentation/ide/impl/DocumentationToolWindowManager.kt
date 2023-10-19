// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.documentation.ToggleShowDocsOnHoverAction
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.gotoByName.ChooseByNameBase
import com.intellij.ide.util.propComponentProperty
import com.intellij.lang.documentation.ide.actions.*
import com.intellij.lang.documentation.ide.ui.DocumentationToolWindowUI
import com.intellij.lang.documentation.ide.ui.DocumentationUI
import com.intellij.lang.documentation.ide.ui.isReusable
import com.intellij.lang.documentation.ide.ui.toolWindowUI
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.platform.backend.documentation.impl.DocumentationRequest
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.swing.JPanel

@Service(Service.Level.PROJECT)
internal class DocumentationToolWindowManager(
  private val project: Project,
  private val cs: CoroutineScope,
) {

  companion object {
    const val TOOL_WINDOW_ID: String = "documentation.v2"

    fun instance(project: Project): DocumentationToolWindowManager = project.service()

    private var autoUpdate_: Boolean by propComponentProperty(name = "documentation.auto.update", defaultValue = true)

    var autoUpdate: Boolean
      get() = autoUpdate_
      set(value) {
        autoUpdate_ = value
        for (openProject in ProjectManager.getInstance().openProjects) {
          openProject.serviceIfCreated<DocumentationToolWindowManager>()?.getReusableContent()?.toolWindowUI?.toggleAutoUpdate(value)
        }
      }
  }

  private val toolWindow: ToolWindowEx

  private var waitForFocusRequest: Boolean = false

  init {
    toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(RegisterToolWindowTask(
      id = TOOL_WINDOW_ID,
      anchor = ToolWindowAnchor.RIGHT,
      icon = AllIcons.Toolwindows.Documentation,
      sideTool = true,
      stripeTitle = IdeBundle.messagePointer("toolwindow.stripe.documentation.v2"),
      shouldBeAvailable = false,
    )) as ToolWindowEx

    toolWindow.setAdditionalGearActions(DefaultActionGroup(
      ActionManager.getInstance().getAction(TOGGLE_SHOW_IN_POPUP_ACTION_ID),
      ToggleShowDocsOnHoverAction(),
      ActionManager.getInstance().getAction(TOGGLE_AUTO_SHOW_ACTION_ID),
      ActionManager.getInstance().getAction(TOGGLE_AUTO_UPDATE_ACTION_ID),
      AdjustFontSizeAction(),
    ))
    toolWindow.setTitleActions(navigationActions())
    toolWindow.installWatcher(toolWindow.contentManager)
    toolWindow.component.putClientProperty(ChooseByNameBase.TEMPORARILY_FOCUSABLE_COMPONENT_KEY, true)
    toolWindow.helpId = "reference.toolWindows.Documentation"
  }

  /**
   * @return `true` if an auto-updating tab is visible, `false` if no such tab exists, or if it is hidden
   */
  fun hasVisibleAutoUpdatingTab(): Boolean = getVisibleAutoUpdatingContent() != null

  /**
   * Orders existing visible reusable tab to display [request].
   *
   * @return `true` if an auto-updating tab is visible, `false` if no such tab exists, or if it is hidden
   */
  fun updateVisibleAutoUpdatingTab(request: DocumentationRequest): Boolean {
    val content = getVisibleAutoUpdatingContent() ?: return false
    content.toolWindowUI.browser.resetBrowser(request)
    return true
  }

  fun getVisibleAutoUpdatingContent(): Content? {
    return getVisibleReusableContent()?.takeIf {
      it.toolWindowUI.isAutoUpdate
    }
  }

  /**
   * @return `true` if a reusable tab is visible, `false` if no such tab exists, or if it is hidden
   */
  fun focusVisibleReusableTab(): Boolean {
    if (!autoUpdate) {
      if (!waitForFocusRequest) {
        return false
      }
      waitForFocusRequest = false
    }
    val content = getVisibleReusableContent() ?: return false
    toolWindow.contentManager.requestFocus(content, false)
    return true
  }

  fun updateVisibleReusableTab(request: DocumentationRequest): Boolean {
    val content = getVisibleReusableContent() ?: return false
    content.toolWindowUI.browser.resetBrowser(request)
    waitForFocusRequest()
    return true
  }

  private fun getVisibleReusableContent(): Content? {
    if (!toolWindow.isVisible) {
      return null
    }
    return toolWindow.contentManager.selectedContent?.takeIf {
      it.toolWindowUI.isReusable
    }
  }

  /**
   * Creates a new reusable tab if no reusable tab exists,
   * orders it to display [requests],
   * and makes it visible.
   */
  fun showInToolWindow(requests: List<DocumentationRequest>) {
    val reusableContent = getReusableContent()
    if (reusableContent == null) {
      val browser = DocumentationBrowser.createBrowser(project, requests)
      showInNewTab(DocumentationUI(project, browser))
    }
    else {
      reusableContent.toolWindowUI.browser.resetBrowser(requests.first())
      makeVisible(reusableContent)
    }
  }

  /**
   * Creates a new reusable tab, or replaces existing tab content, and displays [ui] in it.
   * The [ui] will be disposed once the tab is closed.
   */
  fun showInToolWindow(ui: DocumentationUI) {
    EDT.assertIsEdt()
    val reusableContent = getReusableContent()
    if (reusableContent == null) {
      showInNewTab(ui)
    }
    else {
      Disposer.dispose(reusableContent.toolWindowUI)
      initUI(ui, reusableContent)
      makeVisible(reusableContent)
    }
  }

  private fun showInNewTab(ui: DocumentationUI) {
    val content = addNewContent()
    initUI(ui, content)
    makeVisible(content)
  }

  private fun initUI(ui: DocumentationUI, content: Content) {
    val newUI = DocumentationToolWindowUI(project, ui, content)
    if (autoUpdate) {
      newUI.toggleAutoUpdate(state = true)
    }
    content.component = newUI.contentComponent
  }

  private fun makeVisible(content: Content) {
    toolWindow.contentManager.setSelectedContent(content)
    toolWindow.show()
    waitForFocusRequest()
  }

  private fun getReusableContent(): Content? {
    return toolWindow.contentManager.contents.firstOrNull {
      it.isReusable
    }
  }

  private fun addNewContent(): Content {
    val content = ContentFactory.getInstance().createContent(JPanel(), null, false).also {
      it.isCloseable = true
      it.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
    }
    toolWindow.contentManager.addContent(content)
    return content
  }

  private fun waitForFocusRequest() {
    if (autoUpdate) {
      return
    }
    EDT.assertIsEdt()
    waitForFocusRequest = true
    cs.launch(Dispatchers.EDT) {
      delay(Registry.intValue("documentation.v2.tw.focus.invocation.timeout").toLong())
      waitForFocusRequest = false
    }
  }
}
