// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnusedReceiverParameter")

package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.application.options.schemes.SchemeNameGenerator
import com.intellij.codeInsight.inline.completion.tooltip.onboarding.InlineCompletionOnboardingComponent
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.NlsActions
import com.intellij.util.asSafely
import com.intellij.util.containers.map2Array
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.event.MouseEvent

internal fun InlineCompletionTooltipComponent.shortcutActions(): ActionButtonWithText {
  val predefinedShortcuts = arrayOf(
    "Tab" to KeyboardShortcut.fromString("TAB"),
    "→" to KeyboardShortcut.fromString("RIGHT"),
    "Enter" to KeyboardShortcut.fromString("ENTER"),
    "Shift →" to KeyboardShortcut.fromString("shift pressed RIGHT"),
  )
  val customCurrentShortcut = KeymapUtil.getPrimaryShortcut(IdeActions.ACTION_INSERT_INLINE_COMPLETION)
    .takeIf { predefinedShortcuts.find { (_, shortcut) -> shortcut.toString() == it.toString() } == null }

  val shortcuts = listOfNotNull(
    customCurrentShortcut?.let { KeymapUtil.getShortcutText(it) to it },
    *predefinedShortcuts
  )
  val shortcutActions = shortcuts.map2Array { (name, shortcut) ->
    object : InplaceChangeInlineCompletionShortcutAction(name, shortcut) {
      lateinit var updateAfterActionPerformed: ActionButtonWithText
      override fun actionPerformed(e: AnActionEvent) {
        super.actionPerformed(e)
        updateAfterActionPerformed.update()
      }
    }
  }

  val actions = listOfNotNull<AnAction>(
    Separator.create(IdeBundle.message("inline.completion.tooltip.shortcuts.header")),
    *shortcutActions,
    ChangeToCustomInlineCompletionAction(),
  )

  val group = InlineCompletionPopupActionGroup(actions.toTypedArray())

  return object : ActionButtonWithText(group, group.templatePresentation.clone(), ActionPlaces.UNKNOWN, JBUI.emptySize()) {
    override fun getMargins() = JBUI.insets(1, 2)
    override fun getBorder() = JBUI.Borders.empty()
    override fun shallPaintDownArrow() = true
    override fun isFocusable() = false
    override fun onMousePressed(e: MouseEvent) {
      InlineCompletionOnboardingComponent.getInstance().fireOnboardingFinished()
    }

    @Suppress("HardCodedStringLiteral")
    override fun update() {
      val shortcut = KeymapUtil.getPrimaryShortcut(IdeActions.ACTION_INSERT_INLINE_COMPLETION)
        .asSafely<KeyboardShortcut>()

      presentation.text = when (shortcut?.toString()) {
        null -> " "
        "[pressed TAB]" -> "Tab"
        "[pressed ENTER]" -> "Enter"
        "[shift pressed RIGHT]" -> "Shift →"
        else -> KeymapUtil.getShortcutText(shortcut)
      }
    }
  }.also {
    shortcutActions.forEach { action -> action.updateAfterActionPerformed = it }
  }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

@ApiStatus.Internal
class ChangeToCustomInlineCompletionAction : AnAction(
  IdeBundle.message("inline.completion.tooltip.shortcuts.accept.select.custom"),
), DumbAware, LightEditCompatible {
  override fun actionPerformed(e: AnActionEvent) {
    ShowSettingsUtilImpl.showSettingsDialog(
      e.project,
      "preferences.keymap",
      ActionsBundle.message("action.InsertInlineCompletionAction.text")
    )
  }
}

@ApiStatus.Internal
class InlineCompletionPopupActionGroup(@ApiStatus.Internal val actions: Array<AnAction>) : ActionGroup(), DumbAware {
  init {
    isPopup = true
    templatePresentation.isPerformGroup = true
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> = actions

  override fun actionPerformed(e: AnActionEvent) {
    val popup = JBPopupFactory.getInstance().createActionGroupPopup(
      null, this, e.dataContext,
      JBPopupFactory.ActionSelectionAid.MNEMONICS, true
    )
    PopupUtil.showForActionButtonEvent(popup, e)
  }
}

@ApiStatus.Internal
open class InplaceChangeInlineCompletionShortcutAction(
  @NlsActions.ActionText text: String,
  private val shortcut: Shortcut
) : AnAction(text), DumbAware, LightEditCompatible {

  override fun actionPerformed(e: AnActionEvent) {
    if (!KeymapManager.getInstance().activeKeymap.canModify()) {
      val managerEx = KeymapManager.getInstance() as KeymapManagerEx
      val currentKeymap = managerEx.activeKeymap
      val allKeymaps = managerEx.allKeymaps

      val name = SchemeNameGenerator.getUniqueName(
        KeyMapBundle.message("keymap.with.patched.inline.insert.proposal.name", currentKeymap.getPresentableName())) { name: String ->
        allKeymaps.any { name == it.name || name == it.presentableName }
      }
      val newKeymap = currentKeymap.deriveKeymap(name)
      managerEx.schemeManager.addScheme(newKeymap)
      managerEx.activeKeymap = newKeymap
    }
    val currentKeymap = KeymapManager.getInstance().activeKeymap
    check(currentKeymap.canModify()) {
      "Cannot modify ${currentKeymap.presentableName} keymap"
    }
    currentKeymap.removeAllActionShortcuts(IdeActions.ACTION_INSERT_INLINE_COMPLETION)
    currentKeymap.addShortcut(
      IdeActions.ACTION_INSERT_INLINE_COMPLETION,
      shortcut
    )
  }
}
