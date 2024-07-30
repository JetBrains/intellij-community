// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.codeWithMe.ClientId.Companion.current
import com.intellij.ide.DataManager
import com.intellij.ide.actions.searcheverywhere.SearchListModel.ResultsNotificationElement
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.popup.PopupPositionManager
import com.intellij.ui.popup.PopupUpdateProcessorBase
import com.intellij.util.ui.JBDimension
import java.awt.Component
import javax.swing.JEditorPane

class ShowElementInfoAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val ui = getSEUI(e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)) ?: return
    val infoManager = ElementInfoManager.getInstance(ui)
    e.getData(SearchEverywhereUI.SELECTED_ITEM_INFO)?.let { infoManager.showElementInfo(it, e.project) }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = getSEUI(e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isDumbAware(): Boolean = true

  private fun getSEUI(component: Component?): SearchEverywhereUI? {
    var current = component
    while (current != null) {
      if (current is SearchEverywhereUI) return current
      current = current.parent
    }

    return null
  }
}

private const val POPUP_DIMENSION_KEY = "search.everywhere.element.info.popup"
private const val ELEMENT_INFO_MANAGER_KEY = "SEElementInfoManager"

@Suppress("HardCodedStringLiteral")
private class ElementInfoManager(private val seUI: SearchEverywhereUI) {

  companion object {
    fun getInstance(ui : SearchEverywhereUI): ElementInfoManager {
      var manager = ui.getClientProperty(ELEMENT_INFO_MANAGER_KEY)
      if (manager != null) return manager as ElementInfoManager

      manager = ElementInfoManager(ui)
      ui.putClientProperty(ELEMENT_INFO_MANAGER_KEY, manager)
      return manager
    }
  }

  private var myPopup: AbstractPopup? = null

  fun showElementInfo(info: SearchEverywhereFoundElementInfo, project: Project?) {
    val popup = myPopup
    if (popup?.isVisible == true) {
      fillContent(popup.component as JEditorPane, info)
    }
    else {
      val content = JEditorPane()
      content.preferredSize = JBDimension(250, 150)
      fillContent(content, info)
      showPopup(content, project)
    }
  }

  private fun showPopup(content: JEditorPane, project: Project?) {

    val updater = object : PopupUpdateProcessorBase() {
      override fun updatePopup(element: Any?) {
        val popup = myPopup
        if (popup?.isVisible == true) {
          val single = seUI.selectedInfos.singleOrNull() ?: return
          fillContent(popup.component as JEditorPane, single)
        }
      }
    }

    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, content)
      .setProject(project)
      .addUserData(updater)
      .addUserData(current)
      .setResizable(true)
      .setMovable(true)
      .setFocusable(false)
      .setRequestFocus(false)
      .setCancelOnClickOutside(false)
      .setModalContext(false)
      .setCancelCallback { true }
      .setDimensionServiceKey(project, POPUP_DIMENSION_KEY, false)
      .createPopup() as AbstractPopup

    seUI.registerHint(popup)
    Disposer.register(popup) { seUI.unregisterHint() }
    PopupPositionManager.positionPopupInBestPosition(popup, null, DataManager.getInstance().getDataContext(seUI))

    myPopup = popup
  }

  private fun fillContent(content: JEditorPane, info: SearchEverywhereFoundElementInfo) {
    if (info.element == SearchListModel.MORE_ELEMENT) {
      content.text = "'More...' element"
      return
    }
    if (info.element is ResultsNotificationElement) {
      content.text = "Results notification element"
      return
    }

    content.text = info.description
  }
}