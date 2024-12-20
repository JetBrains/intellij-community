// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lang.lsWidget.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.LafManager
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.platform.lang.lsWidget.LanguageServicePopupSection.ForCurrentFile
import com.intellij.platform.lang.lsWidget.LanguageServiceWidgetItem
import com.intellij.platform.lang.lsWidget.LanguageServiceWidgetItemsProvider
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RowIcon
import com.intellij.util.IconUtil
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import javax.swing.Icon

private const val maxIconsInStatusBar = 3

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
    // If there are more than maxIconsInStatusBar services, then not all icons show up in the status bar.
    // Sorting helps to make sure that icons with an error marker go first.
    val allItems = LanguageServiceWidgetItemsProvider.EP_NAME.extensionList
      .flatMap { it.createWidgetItems(project, file) }
      .sortedByDescending { it.isError }
    cachedWidgetItems = allItems
    if (allItems.isEmpty()) return WidgetState.HIDDEN

    val fileSpecificItems = file?.let { allItems.filter { it.widgetActionLocation == ForCurrentFile } } ?: emptyList()

    val widgetIcon = fileSpecificItems.takeIf { it.isNotEmpty() }?.let { createStatusBarIcon(it) }
                     ?: AllIcons.Json.Object

    val widgetText = when {
      fileSpecificItems.size > maxIconsInStatusBar -> "+${fileSpecificItems.size - maxIconsInStatusBar + 1}"
      else -> ""
    }

    @Suppress("DialogTitleCapitalization")
    val tooltip = when {
      fileSpecificItems.isEmpty() -> LangBundle.message("language.services.widget")
      else -> LangBundle.message("language.services.widget.tooltip.running.on.current.file.list",
                                 fileSpecificItems.joinToString(separator = "<br>") { "- ${it.statusBarTooltip}" })
    }

    return WidgetState(tooltip, widgetText, true).apply {
      icon = widgetIcon
    }
  }

  private fun createStatusBarIcon(widgetItems: List<LanguageServiceWidgetItem>): Icon {
    // either up to 3 icons or 2 icons and "+N" text
    val items = if (widgetItems.size <= maxIconsInStatusBar) widgetItems else widgetItems.subList(0, maxIconsInStatusBar - 1)
    if (items.size == 1) return getStatusBarFriendlyIcon(items[0])

    val separatorIcon = EmptyIcon.create(3, 16)
    val icons = items.flatMap { listOf(getStatusBarFriendlyIcon(it), separatorIcon) }.dropLast(1)
    return RowIcon(*icons.toTypedArray())
  }

  private fun getStatusBarFriendlyIcon(item: LanguageServiceWidgetItem): Icon {
    val statusBarFriendlyColor = when {
      LafManager.getInstance().currentUIThemeLookAndFeel.isDark -> JBUI.CurrentTheme.StatusBar.Widget.FOREGROUND
      else -> JBUI.CurrentTheme.StatusBar.Widget.FOREGROUND.brighter() // without `brighter()` icons are too noisy in light themes
    }
    val statusBarFriendlyIcon = IconUtil.colorize(item.statusBarIcon, statusBarFriendlyColor)
    return when {
      item.isError -> LayeredIcon.layeredIcon(arrayOf(statusBarFriendlyIcon, AllIcons.Nodes.ErrorMark))
      else -> statusBarFriendlyIcon
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
}
