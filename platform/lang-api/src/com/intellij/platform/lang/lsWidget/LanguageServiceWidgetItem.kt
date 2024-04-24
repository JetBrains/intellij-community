// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lang.lsWidget

import com.intellij.icons.AllIcons
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.lang.lsWidget.internal.LanguageServiceWidgetActionsService
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.LayeredIcon.Companion.layeredIcon
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
abstract class LanguageServiceWidgetItem {
  /**
   * The default label for the status bar widget is the generic one: "Language Services".
   *
   * But if
   * - this [LanguageServiceWidgetItem] is the only one in the [LanguageServicePopupSection.ForCurrentFile] popup section
   *   (only for this item the [widgetActionLocation] value is [LanguageServicePopupSection.ForCurrentFile])
   * - and the [statusBarText] value is not `null`
   *
   * then the service-specific text will be shown in the status bar.
   *
   * If this item is not the only one in the [LanguageServicePopupSection.ForCurrentFile] popup section,
   * or it is not in the [LanguageServicePopupSection.ForCurrentFile] popup section at all,
   * then the [statusBarText] value is ignored.
   */
  open val statusBarText: @NlsContexts.StatusBarText String? = null

  /**
   * A tooltip for the status bar widget label.
   * Used only if this item appears to be the only one in the [LanguageServicePopupSection.ForCurrentFile] popup section.
   * Otherwise, it's ignored.
   * @see statusBarText
   */
  open val statusBarTooltip: @NlsContexts.Tooltip String? = null

  /**
   * If `true` then the Platform will add the error mark to the icon in the status bar,
   * and to the action returned by the [createWidgetMainAction] function.
   */
  open val isError: Boolean = false

  abstract val widgetActionLocation: LanguageServicePopupSection

  fun createWidgetAction(): AnAction {
    val mainAction = createWidgetMainAction()
    if (isError) {
      mainAction.templatePresentation.icon = layeredIcon(arrayOf(mainAction.templatePresentation.icon, AllIcons.Nodes.ErrorMark))
    }

    if (ExperimentalUI.isNewUI()) {
      mainAction.templatePresentation.putClientProperty(ActionUtil.INLINE_ACTIONS, createWidgetInlineActions())
      return mainAction
    }
    else {
      return DefaultActionGroup(createWidgetInlineActions()).apply {
        templatePresentation.text = mainAction.templatePresentation.text
        templatePresentation.icon = mainAction.templatePresentation.icon
        templatePresentation.description = mainAction.templatePresentation.description
        templatePresentation.isPopupGroup = true
        templatePresentation.isHideGroupIfEmpty = false
        templatePresentation.isDisableGroupIfEmpty = false
      }
    }
  }

  protected abstract fun createWidgetMainAction(): AnAction

  protected open fun createWidgetInlineActions(): List<AnAction> = emptyList()
}


enum class LanguageServicePopupSection { ForCurrentFile, Other }


/**
 * - When creating an action for [LanguageServiceWidgetItem.createWidgetMainAction], pass item-specific `text` and `icon`
 * - When creating an action for [LanguageServiceWidgetItem.createWidgetInlineActions], pass only [settingsPageClass]
 */
class OpenSettingsAction(
  private val settingsPageClass: Class<out Configurable>,
  text: @NlsActions.ActionText String = LangBundle.message("language.services.widget.open.settings.action"),
  icon: Icon = AllIcons.General.Settings,
) : AnAction(text, null, icon), DumbAware {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    LanguageServiceWidgetActionsService.getInstance().openWidgetItemRelatedSettings(project, settingsPageClass)
  }
}
