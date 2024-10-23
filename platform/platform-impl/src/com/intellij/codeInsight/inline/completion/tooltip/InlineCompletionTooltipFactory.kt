// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JComponent

object InlineCompletionTooltipFactory {
  fun defaultProviderTooltip(
    @Nls comment: String,
    icon: Icon?,
    actions: Array<AnAction>,
  ): JComponent {
    return panel {
      row {
        if (icon != null) {
          icon(icon).gap(RightGap.SMALL)
        }
        comment(comment).gap(RightGap.SMALL)

        val group = InlineCompletionPopupActionGroup(actions)

        val moreActionsButton = object : ActionButton(group, group.templatePresentation.clone(), ActionPlaces.UNKNOWN, JBUI.emptySize()) {
          override fun shallPaintDownArrow() = false
          override fun isFocusable() = false
          override fun getIcon() = AllIcons.Actions.More
        }
        if (actions.isNotEmpty()) {
          cell(moreActionsButton)
        }
      }
    }
  }

  fun defaultProviderTooltip(
    @Nls name: String,
    @Nls comment: String,
    icon: Icon,
    actions: Array<AnAction>,
  ): JComponent {
    return defaultProviderTooltip("$name $comment", icon, actions)
  }
}
