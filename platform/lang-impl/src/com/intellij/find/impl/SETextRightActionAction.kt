// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindBundle
import com.intellij.icons.AllIcons.Actions
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.actionSystem.ex.TooltipLinkProvider
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.DumbAwareToggleAction
import javax.swing.Icon
import javax.swing.JComponent

sealed class SETextRightActionAction(message: String,
                                     icon: Icon,
                                     hoveredIcon: Icon,
                                     selectedIcon: Icon,
                                     private var state: AtomicBooleanProperty,
                                     private val callback: Runnable,
                                     private val myTooltipLink: TooltipLinkProvider.TooltipLink? = null) :
  DumbAwareToggleAction(FindBundle.message(message), null, icon), TooltipLinkProvider, TooltipDescriptionProvider {
  init {
    templatePresentation.hoveredIcon = hoveredIcon
    templatePresentation.selectedIcon = selectedIcon
  }

  override fun getTooltipLink(owner: JComponent?) = myTooltipLink

  fun isSelected() = state.get()
  override fun isSelected(e: AnActionEvent) = isSelected()

  override fun setSelected(e: AnActionEvent, selected: Boolean) {
    state.set(selected)
    callback.run()
  }

  class CaseSensitiveAction(property: AtomicBooleanProperty, onChanged: Runnable) : SETextRightActionAction(
    "find.popup.case.sensitive", Actions.MatchCase, Actions.MatchCaseHovered, Actions.MatchCaseSelected, property, onChanged)

  class WordAction(property: AtomicBooleanProperty, onChanged: Runnable) : SETextRightActionAction(
    "find.whole.words", Actions.Words, Actions.WordsHovered, Actions.WordsSelected, property, onChanged)

  class RegexpAction(property: AtomicBooleanProperty, onChanged: Runnable) : SETextRightActionAction(
    "find.regex", Actions.Regex, Actions.RegexHovered, Actions.RegexSelected, property, onChanged,
    TooltipLinkProvider.TooltipLink(FindBundle.message("find.regex.help.link"), RegExHelpPopup.createRegExLinkRunnable(null)))
}