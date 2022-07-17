/*
 * Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.AnActionButton
import com.intellij.ui.DumbAwareActionButton
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JComponent

object ToolbarUtil {
  @NlsSafe
  val EMPTY_STRING = ""

  fun createActionToolbar(actionGroups: List<List<AnAction>>): JComponent {
    return createActionToolbar(ActionPlaces.UNKNOWN, actionGroups)
  }

  fun createToolbar(place: String, actionHolderGroups: List<List<ActionHolder>>, vararg additionalActions: AnAction): JComponent {
    val actionGroups = actionHolderGroups.map { group ->
      group.map { ToolbarActionButton(it) }
    }
    return createActionToolbar(place, actionGroups, *additionalActions)
  }

  private fun createActionToolbar(place: String, actionGroups: List<List<AnAction>>, vararg additionalActions: AnAction): JComponent {
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
    val actionToolbar = ActionManager.getInstance().createActionToolbar(place, actionGroup, true)
    return actionToolbar.component
  }

  inline fun <reified A : AnAction>createAnActionButton(noinline onClick: () -> Unit): AnActionButton {
    return createAnActionButton(A::class.qualifiedName ?: EMPTY_STRING, onClick)
  }

  inline fun <reified A : AnAction>createAnActionButton(noinline canClick: () -> Boolean, noinline onClick: () -> Unit): AnActionButton {
    return createAnActionButton(A::class.qualifiedName ?: EMPTY_STRING, canClick, onClick)
  }

  fun createAnActionButton(id: String, onClick: Runnable): AnActionButton {
    return createAnActionButton(id) {
      onClick.run()
    }
  }

  fun createAnActionButton(id: String, onClick: () -> Unit): AnActionButton {
    return createAnActionButton(id, { true }, onClick)
  }

  fun createAnActionButton(id: String, canClick: () -> Boolean, onClick: () -> Unit): AnActionButton {
    val holder = createActionHolder(id, canClick, onClick)
    return ToolbarActionButton(holder)
  }

  fun createAnActionButton(holder: ActionHolder): AnActionButton {
    return ToolbarActionButton(holder)
  }

  fun createActionHolder(id: String, onClick: () -> Unit): ActionHolder {
    return createActionHolder(id, { true }, onClick)
  }

  fun createEllipsisToolbar(actions: List<AnAction>): JComponent {
    val ellipsis = createEllipsisActionGroup(actions)
    val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, DefaultActionGroup(ellipsis),
                                                                  false)
    return toolbar.component
  }

  private fun createEllipsisActionGroup(actions: List<AnAction>): ActionGroup {
    return DefaultActionGroup().apply {
      addAll(actions)
      isPopup = true
      with(templatePresentation) {
        putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)
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

  private class ToolbarActionButton(private val holder: ActionHolder) : DumbAwareActionButton() {
    private val fallbackDescription: String?
    private val fallbackIcon: Icon?

    init {
      ActionUtil.copyFrom(this, holder.id)
      fallbackDescription = templatePresentation.description
      fallbackIcon = templatePresentation.icon
    }

    override fun actionPerformed(e: AnActionEvent) {
      holder.onClick()
    }

    override fun updateButton(e: AnActionEvent) {
      val isVisible = holder.checkVisible()
      e.presentation.isVisible = isVisible
      if (isVisible) {
        val isEnabled = holder.canClick
        e.presentation.isEnabled = isEnabled
        e.presentation.icon = createIcon(isEnabled)
        e.presentation.description = createDescription(isEnabled)
      }
    }

    override fun displayTextInToolbar(): Boolean {
      // Note: not a typo. Effectively this means "use description instead of text if the latest is null"
      return templateText.isNullOrBlank()
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
