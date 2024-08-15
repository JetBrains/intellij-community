/*
 * Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.annotations.Nls
import javax.swing.Icon

// TODO Remove all this overengineering.
//  It makes very little sense and breaks EDT/BGT contracts.
//  Use regular AnAction, or build a common superclass where ActionUpdateThread
//  can be properly specified.
@Obsolete
object ToolbarUtil {
  @NlsSafe
  val EMPTY_STRING = ""

  fun createToolbar(place: String, actionHolderGroups: List<List<ActionHolder>>, vararg additionalActions: AnAction): ActionToolbar {
    val actionGroups = actionHolderGroups.map { group ->
      group.map { ToolbarActionButton(it) }
    }
    return createActionToolbar(place, actionGroups, *additionalActions)
  }

  fun createActionToolbar(place: String, actionGroups: List<List<AnAction>>, vararg additionalActions: AnAction): ActionToolbar {
    val actionGroup = DefaultActionGroup().apply {
      for ((index, actionGroup) in actionGroups.withIndex()) {
        if (index > 0) {
          addSeparator()
        }
        for (action in actionGroup) {
          add(action)
        }
      }
    }
    if (additionalActions.isNotEmpty()) {
      actionGroup.addSeparator()
      for (action in additionalActions) {
        actionGroup.add(action)
      }
    }
    return ActionManager.getInstance().createActionToolbar(place, actionGroup, true)
  }

  inline fun <reified A : AnAction>createAnActionButton(noinline onClick: () -> Unit): AnAction {
    return createAnActionButton(A::class.qualifiedName ?: EMPTY_STRING, onClick)
  }

  fun createAnActionButton(id: String, onClick: () -> Unit): AnAction {
    return createAnActionButton(id, { true }, onClick)
  }

  fun createAnActionButton(id: String, canClick: () -> Boolean, onClick: () -> Unit): AnAction {
    val holder = createActionHolder(id, canClick, onClick)
    return ToolbarActionButton(holder)
  }

  fun createAnActionButton(holder: ActionHolder): AnAction {
    return ToolbarActionButton(holder)
  }

  fun createActionHolder(id: String, onClick: () -> Unit): ActionHolder {
    return createActionHolder(id, { true }, onClick)
  }

  fun createEllipsisToolbar(place: String, actions: List<AnAction>): ActionToolbar {
    val ellipsis = createEllipsisActionGroup(actions)
    return ActionManager.getInstance().createActionToolbar(place, DefaultActionGroup(ellipsis), false)
  }

  private fun createEllipsisActionGroup(actions: List<AnAction>): ActionGroup {
    return DefaultActionGroup().apply {
      addAll(actions)
      isPopup = true
      with(templatePresentation) {
        putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true)
        icon = AllIcons.Actions.More
      }
    }
  }

  fun createActionHolder(id: String, canClick: () -> Boolean, onClick: () -> Unit) = object : ActionHolder {
    override val id = id

    override val canClick: Boolean
      get() = canClick()

    override fun onClick() {
      onClick()
    }
  }

  interface ActionHolder {
    val id: String
    val canClick: Boolean
    fun onClick()

    fun checkVisible(): Boolean {
      return true
    }

    @Nls
    fun getHintForDisabled(): String? {
      return null
    }

    fun getAlternativeEnabledIcon(): Icon? {
      return null
    }

    @NlsActions.ActionDescription
    fun getAlternativeEnabledDescription(): String? {
      return null
    }
  }

  private class ToolbarActionButton(private val holder: ActionHolder) : DumbAwareAction() {
    private val fallbackDescription: String?
    private val fallbackIcon: Icon?

    init {
      ActionUtil.copyFrom(this, holder.id)
      fallbackDescription = templatePresentation.description
      fallbackIcon = templatePresentation.icon

      if (templateText.isNullOrBlank()) {
        // Note: not a typo. Effectively this means "use description instead of text if the latest is null"
        templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
      }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(e: AnActionEvent) {
      holder.onClick()
    }

    override fun update(e: AnActionEvent) {
      val isVisible = holder.checkVisible()
      e.presentation.isVisible = isVisible
      if (isVisible) {
        val isEnabled = holder.canClick
        e.presentation.isEnabled = isEnabled
        e.presentation.icon = createIcon(isEnabled)
        e.presentation.description = createDescription(isEnabled)
      }
    }

    private fun createIcon(isEnabled: Boolean): Icon? {
      return holder.getAlternativeEnabledIcon()?.takeIf { isEnabled } ?: fallbackIcon
    }

    @NlsActions.ActionDescription
    private fun createDescription(isEnabled: Boolean): String? {
      return if (isEnabled) createEnabledDescription() else createDisabledDescription()
    }

    @Nls
    private fun createEnabledDescription(): String? {
      return holder.getAlternativeEnabledDescription() ?: fallbackDescription
    }

    @Nls
    private fun createDisabledDescription(): String? {
      return holder.getHintForDisabled()?.let { createDescriptionWithHint(it) } ?: fallbackDescription
    }

    @NlsSafe
    private fun createDescriptionWithHint(hint: String): String? {
      return fallbackDescription?.let { "$it ($hint)" }
    }
  }
}
