// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.CopyPasteDelegator
import com.intellij.ide.CopyPasteSupport
import com.intellij.ide.navbar.impl.DefaultNavBarItem
import com.intellij.ide.navbar.vm.NavBarVmItem
import com.intellij.ide.navigationToolbar.NavBarPanel
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.ui.PopupHandler
import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.containers.JBIterable
import org.apache.commons.lang.StringUtils
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel


internal class NavBarItemComponent(
  val item: NavBarVmItem,
  val project: Project,
  isIconNeeded: Boolean = false,
  isChevronNeeded: Boolean = true,
) : JPanel(BorderLayout()), DataProvider {

  init {
    val presentation = item.presentation
    val coloredComponent = SimpleColoredComponent().apply {
      if (isIconNeeded) {
        icon = presentation.icon
      }
      append(StringUtils.abbreviate(presentation.text, 50), presentation.textAttributes)
    }

    border = BorderFactory.createEmptyBorder(0, 5, 0, 0)

    add(coloredComponent, BorderLayout.CENTER)

    if (isChevronNeeded) {
      add(JLabel(AllIcons.Ide.NavBarSeparator), BorderLayout.EAST)
    }

    addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) {
        val actionGroup = object : ActionGroup() {
          override fun getChildren(e: AnActionEvent?): Array<AnAction> {
            if (e == null) return EMPTY_ARRAY
            val popupGroupId: String = IdeActions.GROUP_NAVBAR_POPUP
            val group = CustomActionsSchema.getInstance().getCorrectedAction(popupGroupId) as ActionGroup?
            return group?.getChildren(e) ?: EMPTY_ARRAY
          }
        }
        val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.NAVIGATION_BAR_POPUP, actionGroup)
        popupMenu.setTargetComponent(this@NavBarItemComponent)
        val menu = popupMenu.component

        menu.show(this@NavBarItemComponent, x, y)
      }
    })

  }

  override fun getData(dataId: String): Any? = getData2(dataId, item, project)
}

// TODO extract super class to reuse most of the bar and popup components
internal class NavigationBarPopupItemComponent(
  val item: NavBarVmItem,
  val project: Project,
) : SimpleColoredComponent(), DataProvider {

  init {
    val presentation = item.presentation
    isTransparentIconBackground = true
    icon = presentation.icon
    append(StringUtils.abbreviate(presentation.popupText, 50), presentation.textAttributes)

    // TODO: prevent handling this event by item list in popup
    addMouseListener(object : PopupHandler() {
      override fun invokePopup(comp: Component, x: Int, y: Int) {
        val actionGroup = object : ActionGroup() {
          override fun getChildren(e: AnActionEvent?): Array<AnAction> {
            if (e == null) return EMPTY_ARRAY
            val popupGroupId: String = IdeActions.GROUP_NAVBAR_POPUP
            val group = CustomActionsSchema.getInstance().getCorrectedAction(popupGroupId) as ActionGroup?
            return group?.getChildren(e) ?: EMPTY_ARRAY
          }
        }
        val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.NAVIGATION_BAR_POPUP, actionGroup)
        popupMenu.setTargetComponent(this@NavigationBarPopupItemComponent)
        val menu = popupMenu.component

        menu.show(this@NavigationBarPopupItemComponent, x, y)
      }
    })

  }

  override fun getData(dataId: String): Any? = getData2(dataId, item, project)
}


private fun JComponent.getData2(dataId: String, item: NavBarVmItem, project: Project): Any? {
  return when {
    PlatformDataKeys.CONTEXT_COMPONENT.`is`(dataId) -> this
    CommonDataKeys.PROJECT.`is`(dataId) -> project.takeUnless { it.isDisposed }
    PlatformDataKeys.BGT_DATA_PROVIDER.`is`(dataId) -> slowDataProvider(item, project)
    PlatformDataKeys.CUT_PROVIDER.`is`(dataId) -> getCopyPasteDelegator(project).cutProvider
    PlatformDataKeys.COPY_PROVIDER.`is`(dataId) -> getCopyPasteDelegator(project).copyProvider
    PlatformDataKeys.PASTE_PROVIDER.`is`(dataId) -> getCopyPasteDelegator(project).pasteProvider
    else -> null
  }
}

private fun slowDataProvider(item: NavBarVmItem, project: Project) = DataProvider { slowId ->
  // TODO read action?
  val backItem = item.pointer.dereference() ?: return@DataProvider null
  val defaultItem = backItem as? DefaultNavBarItem<*> ?: return@DataProvider null
  NavBarPanel.getSlowData(slowId, project, JBIterable.of(defaultItem.data))
}

private fun JComponent.getCopyPasteDelegator(project: Project): CopyPasteSupport {
  val key = "NavBarPanel.copyPasteDelegator"
  val result = getClientProperty(key)
  if (result is CopyPasteSupport) {
    return result
  }
  else {
    return CopyPasteDelegator(project, this)
      .also { newDelegator -> putClientProperty(key, newDelegator) }
  }
}
