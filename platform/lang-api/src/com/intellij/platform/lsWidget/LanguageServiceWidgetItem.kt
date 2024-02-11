// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsWidget

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.LayeredIcon.Companion.layeredIcon
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
abstract class LanguageServiceWidgetItem(val context: LanguageServiceWidgetContext) {

  abstract val statusBarText: String

  open val statusBarTooltip: String? = null

  open val isError: Boolean = false

  abstract val widgetActionLocation: LanguageServicePopupSection

  fun createWidgetAction(): AnAction =
    createWidgetMainAction().apply {
      if (isError) {
        templatePresentation.icon = layeredIcon(arrayOf(templatePresentation.icon, AllIcons.Nodes.ErrorMark))
      }
      templatePresentation.putClientProperty(ActionUtil.INLINE_ACTIONS, createWidgetInlineActions())
    }

  protected abstract fun createWidgetMainAction(): AnAction

  protected open fun createWidgetInlineActions(): List<AnAction> = emptyList()
}


enum class LanguageServicePopupSection { ForCurrentFile, Other }


class OpenSettingsAction(
  text: @NlsActions.ActionText String,
  icon: Icon,
  private val settingsPageClass: Class<out Configurable>,
) : AnAction(text, null, icon), DumbAware {

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { ShowSettingsUtil.getInstance().showSettingsDialog(e.project, settingsPageClass) }
  }
}
