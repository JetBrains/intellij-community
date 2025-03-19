// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.ui

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.documentation.DocumentationHtmlUtil
import com.intellij.codeInsight.documentation.DocumentationHtmlUtil.contentInnerPadding
import com.intellij.codeInsight.documentation.DocumentationHtmlUtil.contentOuterPadding
import com.intellij.codeInsight.documentation.DocumentationHtmlUtil.settingsButtonPadding
import com.intellij.codeInsight.documentation.DocumentationHtmlUtil.spaceBeforeParagraph
import com.intellij.codeInsight.documentation.DocumentationManager.NEW_JAVADOC_LOCATION_AND_SIZE
import com.intellij.codeInsight.documentation.ToggleShowDocsOnHoverAction
import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.codeInsight.hint.LineTooltipRenderer
import com.intellij.lang.documentation.ide.actions.*
import com.intellij.lang.documentation.ide.impl.DocumentationBrowser
import com.intellij.lang.documentation.ide.impl.DocumentationToolWindowManager
import com.intellij.lang.documentation.ide.impl.DocumentationToolWindowManager.Companion.TOOL_WINDOW_ID
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.MoreActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.DimensionService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.EDT
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPanel

internal class DocumentationPopupUI(
  private val project: Project,
  ui: DocumentationUI,
) : Disposable {

  private var _ui: DocumentationUI? = ui
  val ui: DocumentationUI get() = requireNotNull(_ui) { "already detached" }
  val browser: DocumentationBrowser get() = ui.browser

  val component: JComponent
  val preferableFocusComponent: JComponent get() = ui.editorPane

  val coroutineScope: CoroutineScope = CoroutineScope(Job())
  private val popupUpdateFlow = MutableSharedFlow<PopupUpdateEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private lateinit var myPopup: AbstractPopup

  init {
    val editorPane = ui.editorPane
    browser.closeTrigger {
      coroutineScope.launch(Dispatchers.EDT) {
        myPopup.cancel()
      }
    }
    val primaryActions = primaryActions().toMutableList()
    val secondaryActions = ArrayList<AnAction>()
    val openInToolwindowAction = OpenInToolwindowAction()
    secondaryActions.add(openInToolwindowAction)
    secondaryActions.add(ActionManager.getInstance().getAction(TOGGLE_SHOW_IN_POPUP_ACTION_ID))
    secondaryActions.add(ToggleShowDocsOnHoverAction())
    secondaryActions.add(ActionManager.getInstance().getAction(TOGGLE_AUTO_SHOW_ACTION_ID))
    secondaryActions.add(AdjustFontSizeAction())
    secondaryActions.add(RestoreDefaultSizeAction())

    val editSourceAction = ActionManager.getInstance().getAction(EDIT_SOURCE_ACTION_ID)
    val navigationGroup = ActionManager.getInstance().getAction(NAVIGATION_GROUP_ID)
    primaryActions[primaryActions.indexOf(navigationGroup)] = DefaultActionGroup().apply {
      copyFromGroup(navigationGroup as DefaultActionGroup)
      remove(editSourceAction)
    }

    val gearActions: DefaultActionGroup = MoreActionGroup()
    gearActions.addAll(secondaryActions)
    gearActions.addSeparator()
    gearActions.addAll(primaryActions)

    val corner = toolbarComponent(DefaultActionGroup(editSourceAction, gearActions), editorPane).apply {
      border = JBUI.Borders.empty(0, 0, contentOuterPadding - 3, settingsButtonPadding - 5)
    }
    ui.trackDocumentationBackgroundChange(this) {
      corner.background = it
    }
    val pane = DocumentationPopupPane(ui.scrollPane)
    pane.add(scrollPaneWithCorner(this, ui.scrollPane, corner), BorderLayout.CENTER)
    pane.add(ui.switcherToolbarComponent, BorderLayout.NORTH)
    updatePaddings(corner)
    corner.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        updatePaddings(corner)
        popupUpdateFlow.tryEmit(PopupUpdateEvent.ToolbarSizeChanged)
      }
    })
    component = UiDataProvider.wrapComponent(pane) { sink ->
      sink[DOCUMENTATION_POPUP] = myPopup
    }

    openInToolwindowAction.registerCustomShortcutSet(component, this)
    coroutineScope.launch {
      popupUpdateFlow.emitAll(ui.contentSizeUpdates)
    }
  }

  override fun dispose() {
    coroutineScope.cancel()
    val ui = _ui
    if (ui != null) {
      Disposer.dispose(ui)
      _ui = null
    }
  }

  fun jointHover() {
    // TODO ? separate DocumentationJointHoverUI class
    val bg = UIUtil.getToolTipActionBackground()
    Disposer.register(this, ui.setBackground(bg))
    component.background = bg
    component.border = IdeBorderFactory.createBorder(UIUtil.getTooltipSeparatorColor(), SideBorder.TOP)
  }

  fun setPopup(popup: AbstractPopup) {
    Disposer.register(popup, this)
    myPopup = popup
    ui.editorPane.setHint(popup)
  }

  fun updatePopup(updater: suspend (PopupUpdateEvent) -> Unit) {
    coroutineScope.launch(Dispatchers.EDT) {
      popupUpdateFlow.collectLatest {
        updater(it)
      }
    }
  }

  private fun updatePaddings(toolbar: JComponent) {
    ui.locationLabel.border = JBUI.Borders.empty(
      2 + spaceBeforeParagraph, LineTooltipRenderer.CONTENT_PADDING,
      2 + contentOuterPadding, 2 + (toolbar.width / JBUIScale.scale(1f)).toInt())
    val editorPreferredSize = ui.editorPane.preferredSize
    val viewPanel = ui.scrollPane.viewport.view as JPanel
    if (editorPreferredSize.height < toolbar.height * 2
        && !ui.locationLabel.isVisible
        && editorPreferredSize.width + toolbar.width > JBUIScale.scale(DocumentationHtmlUtil.docPopupMinWidth)
    ) {
      viewPanel.border = JBUI.Borders.emptyRight(
        (toolbar.width / JBUIScale.scale(1f)).toInt() - contentOuterPadding - contentInnerPadding - 10)
    }
    else {
      viewPanel.border = JBUI.Borders.empty()
    }
  }

  private fun detachUI(): DocumentationUI {
    EDT.assertIsEdt()
    browser.clearCloseTrigger()
    val ui = ui
    _ui = null
    return ui
  }

  private inner class OpenInToolwindowAction : AnAction(
    CodeInsightBundle.messagePointer("action.Documentation.OpenInToolWindow.text"),
    CodeInsightBundle.messagePointer("action.Documentation.OpenInToolWindow.description"),
    ToolWindowManager.getInstance(project).getLocationIcon(TOOL_WINDOW_ID, EmptyIcon.ICON_16),
  ), ActionToIgnore {

    init {
      shortcutSet = ActionManager.getInstance().getAction(IdeActions.ACTION_QUICK_JAVADOC).shortcutSet
    }

    override fun actionPerformed(e: AnActionEvent) {
      val documentationUI = detachUI()
      myPopup.cancel()
      DocumentationToolWindowManager.getInstance(project).showInToolWindow(documentationUI)
    }
  }

  private var manuallyResized = false

  fun useStoredSize(): Supplier<Boolean> {
    EDT.assertIsEdt()
    myPopup.addResizeListener(PopupResizeListener(), this)
    val storedSize = DimensionService.getInstance().getSize(NEW_JAVADOC_LOCATION_AND_SIZE, project)
    if (storedSize != null && storedSize.width > 50 && storedSize.height > 50) {
      manuallyResized = true
      myPopup.size = storedSize
    }
    // this makes impossible to use manuallyResized without installing listener
    return Supplier {
      EDT.assertIsEdt()
      manuallyResized
    }
  }

  private fun restoreSize() {
    manuallyResized = false
    DimensionService.getInstance().setSize(NEW_JAVADOC_LOCATION_AND_SIZE, null, project)
    popupUpdateFlow.tryEmit(PopupUpdateEvent.RestoreSize)
  }

  private inner class PopupResizeListener : Runnable {

    override fun run() {
      manuallyResized = true
      DimensionService.getInstance().setSize(NEW_JAVADOC_LOCATION_AND_SIZE, myPopup.contentSize, project)
    }
  }

  private inner class RestoreDefaultSizeAction : AnAction(CodeInsightBundle.messagePointer("javadoc.restore.size")), ActionToIgnore {

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = manuallyResized
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
      restoreSize()
    }
  }
}
