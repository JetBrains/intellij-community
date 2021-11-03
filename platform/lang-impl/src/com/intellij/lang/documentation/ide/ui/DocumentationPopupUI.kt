// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.ui

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.documentation.DocumentationManager.NEW_JAVADOC_LOCATION_AND_SIZE
import com.intellij.codeInsight.documentation.PopupDragListener
import com.intellij.codeInsight.documentation.ToggleShowDocsOnHoverAction
import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.ide.DataManager
import com.intellij.lang.documentation.ide.actions.*
import com.intellij.lang.documentation.ide.impl.DocumentationBrowser
import com.intellij.lang.documentation.ide.impl.DocumentationToolWindowManager
import com.intellij.lang.documentation.ide.impl.DocumentationToolWindowManager.Companion.TOOL_WINDOW_ID
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.DimensionService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.EDT
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.addPropertyChangeListener
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import java.awt.BorderLayout
import java.util.function.Supplier
import javax.swing.JComponent

internal class DocumentationPopupUI(
  private val project: Project,
  ui: DocumentationUI,
) : Disposable {

  private var _ui: DocumentationUI? = ui
  private val ui: DocumentationUI get() = requireNotNull(_ui) { "already detached" }
  val browser: DocumentationBrowser get() = ui.browser

  private val toolbarComponent: JComponent
  private val corner: JComponent

  val component: JComponent
  val preferableFocusComponent: JComponent get() = ui.editorPane

  val coroutineScope: CoroutineScope = CoroutineScope(Job())
  private val popupUpdateFlow = MutableSharedFlow<Any?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private lateinit var myPopup: AbstractPopup

  init {
    val editorPane = ui.editorPane

    Disposer.register(this, ui.addContentListener {
      popupUpdateFlow.tryEmit("content change")
    })
    editorPane.addPropertyChangeListener(this, "font") {
      popupUpdateFlow.tryEmit("font change")
    }

    val primaryActions: List<AnAction> = primaryActions()
    val secondaryActions = ArrayList<AnAction>()
    val openInToolwindowAction = OpenInToolwindowAction()
    secondaryActions.add(openInToolwindowAction)
    secondaryActions.add(ActionManager.getInstance().getAction(TOGGLE_SHOW_IN_POPUP_ACTION_ID))
    secondaryActions.add(ToggleShowDocsOnHoverAction())
    secondaryActions.add(ActionManager.getInstance().getAction(TOGGLE_AUTO_SHOW_ACTION_ID))
    secondaryActions.add(AdjustFontSizeAction())
    secondaryActions.add(ShowToolbarAction())
    secondaryActions.add(RestoreDefaultSizeAction())

    val toolbarActionGroup = DefaultActionGroup()
    toolbarActionGroup.addAll(primaryActions)
    for (secondaryAction in secondaryActions) {
      toolbarActionGroup.addAction(secondaryAction).setAsSecondary(true)
    }

    val gearActions: DefaultActionGroup = DefaultActionGroup().also { it.isPopup = true }
    gearActions.addAll(secondaryActions)
    gearActions.addSeparator()
    gearActions.addAll(primaryActions)

    toolbarComponent = toolbarComponent(toolbarActionGroup, editorPane)
    corner = actionButton(gearActions, editorPane)
    component = DocumentationPopupPane(ui.scrollPane).also {
      it.add(toolbarComponent, BorderLayout.NORTH)
      it.add(scrollPaneWithCorner(this, ui.scrollPane, corner), BorderLayout.CENTER)
    }

    openInToolwindowAction.registerCustomShortcutSet(component, this)

    showToolbar(Registry.get("documentation.show.toolbar").asBoolean())
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
    toolbarComponent.background = bg
    component.background = bg
    component.border = IdeBorderFactory.createBorder(UIUtil.getTooltipSeparatorColor(), SideBorder.TOP)
  }

  fun setPopup(popup: AbstractPopup) {
    Disposer.register(popup, this)
    myPopup = popup

    DataManager.registerDataProvider(component) { dataId ->
      if (DOCUMENTATION_POPUP.`is`(dataId)) {
        popup
      }
      else {
        null
      }
    }

    val editorPane = ui.editorPane
    editorPane.setHint(popup)
    PopupDragListener.dragPopupByComponent(popup, toolbarComponent)
  }

  fun updatePopup(updater: suspend () -> Unit) {
    coroutineScope.launch(Dispatchers.EDT) {
      popupUpdateFlow.collectLatest {
        updater()
      }
    }
  }

  private fun detachUI(): DocumentationUI {
    EDT.assertIsEdt()
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
      DocumentationToolWindowManager.instance(project).showInToolWindow(documentationUI)
    }
  }

  private fun showToolbar(value: Boolean) {
    toolbarComponent.isVisible = value
    corner.isVisible = !value
    popupUpdateFlow.tryEmit("toolbar")
  }

  private inner class ShowToolbarAction : ToggleAction(
    CodeInsightBundle.messagePointer("javadoc.show.toolbar"),
  ), ActionToIgnore {

    override fun isSelected(e: AnActionEvent): Boolean {
      return Registry.get("documentation.show.toolbar").asBoolean()
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      Registry.get("documentation.show.toolbar").setValue(state)
      showToolbar(state)
    }
  }

  private var manuallyResized = false

  fun useStoredSize(): Supplier<Boolean> {
    EDT.assertIsEdt()
    myPopup.addResizeListener(PopupResizeListener(), this)
    val storedSize = DimensionService.getInstance().getSize(NEW_JAVADOC_LOCATION_AND_SIZE, project)
    if (storedSize != null) {
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
    popupUpdateFlow.tryEmit("restore size")
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

    override fun actionPerformed(e: AnActionEvent) {
      restoreSize()
    }
  }
}
