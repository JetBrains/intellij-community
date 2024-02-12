// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lang.lsWidget.impl

import com.intellij.icons.AllIcons
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.platform.lang.lsWidget.LanguageServicePopupSection.ForCurrentFile
import com.intellij.platform.lang.lsWidget.LanguageServiceWidgetContext
import com.intellij.platform.lang.lsWidget.LanguageServiceWidgetItemsProvider
import com.intellij.ui.LayeredIcon
import com.intellij.util.ui.JBDimension
import kotlinx.coroutines.CoroutineScope
import javax.swing.Icon

internal class LanguageServiceWidget(project: Project, scope: CoroutineScope) : EditorBasedStatusBarPopup(project, false, scope) {

  override fun ID(): String = LanguageServiceWidgetFactory.ID

  override fun createInstance(project: Project): StatusBarWidget = LanguageServiceWidget(project, scope)

  override fun getWidgetState(file: VirtualFile?): WidgetState {
    val allItems = getAllWidgetItems(LanguageServiceWidgetContext(project, file, ::update, this))
    if (allItems.isEmpty()) return WidgetState.HIDDEN

    val fileSpecificItems = file?.let { allItems.filter { it.widgetActionLocation == ForCurrentFile } } ?: emptyList()
    val singleFileSpecificItem = fileSpecificItems.singleOrNull()
    val text = singleFileSpecificItem?.statusBarText ?: LangBundle.message("language.services.widget")
    val tooltip = singleFileSpecificItem?.statusBarTooltip
    val isError = fileSpecificItems.any { it.isError } // or maybe `singleFileSpecificItem?.isError ?: allItems.any { it.isError }`?

    return WidgetState(tooltip, text, true).apply {
      icon = if (isError) errorIcon else normalIcon
    }
  }

  override fun createPopup(context: DataContext): ListPopup =
    JBPopupFactory.getInstance().createActionGroupPopup(
      LangBundle.message("language.services.widget"),
      createActionGroup(context),
      context,
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      true
    ).apply {
      setMinimumSize(JBDimension(250, 1))
    }

  private fun createActionGroup(dataContext: DataContext): ActionGroup {
    val file = dataContext.getData(CommonDataKeys.VIRTUAL_FILE)
    val allItems = getAllWidgetItems(LanguageServiceWidgetContext(project, file, ::update, this))
    val fileSpecificItems = file?.let { allItems.filter { it.widgetActionLocation == ForCurrentFile } } ?: emptyList()
    val otherItems = allItems - fileSpecificItems.toSet()
    // The '---Other---' separator doesn't look great if it's the only separator in the popup, so check only `fileSpecificStates.isNotEmpty()`
    val needSeparators = fileSpecificItems.isNotEmpty()

    val group = DefaultActionGroup()

    if (needSeparators) group.addSeparator(LangBundle.message("language.services.widget.for.current.file"))
    fileSpecificItems.forEach { group.add(it.createWidgetAction()) }

    if (needSeparators) group.addSeparator(LangBundle.message("language.services.widget.for.other.files"))
    otherItems.forEach { group.add(it.createWidgetAction()) }

    return group
  }

  private companion object {
    private fun getAllWidgetItems(context: LanguageServiceWidgetContext) =
      LanguageServiceWidgetItemsProvider.EP_NAME.extensionList.flatMap {
        it.getWidgetItems(context)
      }

    private val normalIcon: Icon = AllIcons.Json.Object
    private val errorIcon: Icon = LayeredIcon.layeredIcon { arrayOf(AllIcons.Json.Object, AllIcons.Nodes.ErrorMark) }
  }
}
