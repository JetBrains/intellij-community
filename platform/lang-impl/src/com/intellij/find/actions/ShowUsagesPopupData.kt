// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.ui.popup.AbstractPopup
import com.intellij.usages.UsageView
import com.intellij.usages.impl.UsageViewStatisticsCollector.Companion.logOpenInFindToolWindow
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JTable

internal class ShowUsagesPopupData(@JvmField val parameters: ShowUsagesParameters, @JvmField val table: JTable,
                                   @JvmField val actionHandler: ShowUsagesActionHandler, @JvmField val usageView: UsageView) {

  @JvmField
  val popupRef = AtomicReference<AbstractPopup>()

  @JvmField
  val pinGroup = DefaultActionGroup()

  @JvmField
  val header = ShowUsagesHeader(createPinButton(parameters.project, popupRef, pinGroup, table, actionHandler::findUsages),
                                actionHandler.presentation.searchTargetString)

  private fun createPinButton(project: Project,
                              popupRef: AtomicReference<AbstractPopup>,
                              pinGroup: DefaultActionGroup,
                              table: JTable,
                              findUsagesRunnable: Runnable): JComponent {
    val icon = getInstance(project).getLocationIcon(ToolWindowId.FIND, AllIcons.General.Pin_tab)
    val pinAction: AnAction = object : AnAction(IdeBundle.messagePointer("show.in.find.window.button.name"),
                                                IdeBundle.messagePointer("show.in.find.window.button.pin.description"), icon) {
      init {
        val action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES)
        shortcutSet = action.shortcutSet
      }

      override fun actionPerformed(e: AnActionEvent) {
        logOpenInFindToolWindow(project, usageView)
        ShowUsagesAction.hideHints()
        ShowUsagesAction.cancel(popupRef.get())
        findUsagesRunnable.run()
      }
    }
    pinGroup.add(pinAction)
    val pinToolbar = ShowUsagesAction.createActionToolbar(table, pinGroup)
    val result = pinToolbar.component
    result.border = null
    result.isOpaque = false
    return result
  }
}
