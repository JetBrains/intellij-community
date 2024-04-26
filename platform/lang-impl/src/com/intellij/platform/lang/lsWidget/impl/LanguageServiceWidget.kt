// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lang.lsWidget.impl

import com.intellij.icons.AllIcons
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.platform.lang.lsWidget.LanguageServicePopupSection.ForCurrentFile
import com.intellij.platform.lang.lsWidget.LanguageServiceWidgetItem
import com.intellij.platform.lang.lsWidget.LanguageServiceWidgetItemsProvider
import com.intellij.ui.LayeredIcon
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBDimension
import kotlinx.coroutines.CoroutineScope
import javax.swing.Icon

internal class LanguageServiceWidget(project: Project, scope: CoroutineScope) : EditorBasedStatusBarPopup(project, false, scope) {
  /**
   * This cache helps to perform some calculations in BGT (in [getWidgetState])
   * and then to use the cached result in EDT (in [createActionGroup]).
   * Specifically, implementing [LanguageServiceWidgetItem.widgetActionLocation] as `by lazy {...}`
   * helps to avoid running costly calculations in EDT.
   */
  private var cachedWidgetItems: List<LanguageServiceWidgetItem> = emptyList()

  override fun ID(): String = LANGUAGE_SERVICES_WIDGET_ID

  override fun createInstance(project: Project): StatusBarWidget = LanguageServiceWidget(project, scope)

  override fun registerCustomListeners(connection: MessageBusConnection) {
    LanguageServiceWidgetItemsProvider.EP_NAME.extensionList.forEach { it.registerWidgetUpdaters(project, connection, ::update) }
  }

  override fun getWidgetState(file: VirtualFile?): WidgetState {
    if (!Registry.`is`("language.service.status.bar.widget")) return WidgetState.HIDDEN

    val allItems = LanguageServiceWidgetItemsProvider.EP_NAME.extensionList.flatMap { it.createWidgetItems(project, file) }
    cachedWidgetItems = allItems
    if (allItems.isEmpty()) return WidgetState.HIDDEN

    val fileSpecificItems = file?.let { allItems.filter { it.widgetActionLocation == ForCurrentFile } } ?: emptyList()
    val singleFileSpecificItem = fileSpecificItems.singleOrNull()
    val text = singleFileSpecificItem?.statusBarText ?: LangBundle.message("language.services.widget")
    val maxToolbarTextLength = 30
    val shortenedText = StringUtil.shortenTextWithEllipsis(text, maxToolbarTextLength, 0, true)
    val tooltip = singleFileSpecificItem?.statusBarTooltip ?: if (text.length > maxToolbarTextLength) text else null
    val isError = fileSpecificItems.any { it.isError } // or maybe `allItems.any { it.isError }`?

    return WidgetState(tooltip, shortenedText, true).apply {
      icon = if (isError) Icons.errorIcon else Icons.normalIcon
    }
  }

  override fun createPopup(context: DataContext): ListPopup =
    JBPopupFactory.getInstance().createActionGroupPopup(
      LangBundle.message("language.services.widget"),
      createActionGroup(),
      context,
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
      true
    ).apply {
      setMinimumSize(JBDimension(300, 1))
    }

  private fun createActionGroup(): ActionGroup {
    val allItems = cachedWidgetItems
    val fileSpecificItems = allItems.filter { it.widgetActionLocation == ForCurrentFile }
    val otherItems = allItems - fileSpecificItems.toSet()

    val group = DefaultActionGroup()

    group.addSeparator(LangBundle.message("language.services.widget.section.running.on.current.file"))
    if (fileSpecificItems.isNotEmpty()) {
      fileSpecificItems.forEach { group.add(it.createWidgetAction()) }
    }
    else {
      group.add(NoServices)
    }

    group.addSeparator(LangBundle.message("language.services.widget.section.running.on.other.files"))
    otherItems.forEach { group.add(it.createWidgetAction()) }

    return group
  }

  private object NoServices : AnAction(LangBundle.messagePointer("language.services.widget.no.services")), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = false
    }

    override fun actionPerformed(e: AnActionEvent) {}
  }

  private object Icons {
    val normalIcon: Icon = AllIcons.Json.Object
    val errorIcon: Icon = LayeredIcon.layeredIcon(arrayOf(AllIcons.Json.Object, AllIcons.Nodes.ErrorMark))
  }
}
