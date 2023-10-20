// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.LightweightHint
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.util.preferredHeight
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import java.awt.Insets
import java.awt.event.KeyEvent


internal object InlineCompletionTooltip {
  private val tooltipKey = Key<Unit>("EDITOR_HAS_INLINE_TOOLTIP")
  @RequiresEdt
  fun enterHover(session: InlineCompletionSession) {
    val editor = session.context.editor
    if (tooltipKey.isIn(editor)) {
      return
    }
    val activeLookup = LookupManager.getActiveLookup(editor)

    if (activeLookup?.isPositionedAboveCaret == true) {
      return
    }

    val panel = createPanel(session)

    val hint = object : LightweightHint(panel) {
      override fun onPopupCancel() {
        // on hint hide
        editor.putUserData(tooltipKey, null)
      }
    }.apply {
      setForceShowAsPopup(true)
    }

    val location = HintManagerImpl.getHintPosition(
      hint,
      editor,
      editor.offsetToLogicalPosition(editor.caretModel.offset),
      HintManager.ABOVE
    )
    location.y -= panel.preferredHeight

    HintManagerImpl.getInstanceImpl().showEditorHint(
      hint,
      editor,
      location,
      HintManager.HIDE_BY_CARET_MOVE or HintManager.HIDE_BY_TEXT_CHANGE or HintManager.HIDE_BY_SCROLLING,
      0,
      false,
      HintManagerImpl.createHintHint(editor, location, hint, HintManager.ABOVE).setContentActive(false)
    )
    editor.putUserData(tooltipKey, Unit)
    Disposer.register(session) {
      hint.hide()
    }
  }

  private class InplaceChangeInlineCompletionShortcutAction(@NlsActions.ActionText text: String, private val shortcut: Shortcut) :
    AnAction(text), DumbAware, LightEditCompatible {

    lateinit var updateAfterActionPerformed: ActionButtonWithText
    override fun actionPerformed(e: AnActionEvent) {
      KeymapManager.getInstance().activeKeymap.removeAllActionShortcuts(IdeActions.ACTION_INSERT_INLINE_COMPLETION)
      KeymapManager.getInstance().activeKeymap.addShortcut(
        IdeActions.ACTION_INSERT_INLINE_COMPLETION,
        shortcut
      )
      updateAfterActionPerformed.update()
    }

  }

  private class ChangeToCustomInlineCompletionAction : AnAction(
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

  private fun createPanel(session: InlineCompletionSession): DialogPanel {
    val panel = panel {
      row {
        cell(createSelectInsertShortcutAction()).gap(RightGap.SMALL)
        @Suppress("DialogTitleCapitalization")
        text(IdeBundle.message("inline.completion.tooltip.shortcuts.accept.description")).gap(RightGap.SMALL)
        cell(session.provider.providerPresentation.getTooltip(session.context.editor.project))
      }
    }.apply {
      border = JBUI.Borders.empty(1, 4)
    }
    return panel
  }

  private class SelectShortcutActionGroup(private val actions: Array<AnAction>) : ActionGroup(), DumbAware {
    init {
      isPopup = true
      templatePresentation.isPerformGroup = true
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> = actions

    override fun actionPerformed(e: AnActionEvent) {
      val popup = JBPopupFactory.getInstance().createActionGroupPopup(null, this, e.dataContext,
                                                                      JBPopupFactory.ActionSelectionAid.MNEMONICS, true)
      PopupUtil.showForActionButtonEvent(popup, e)
    }
  }

  private fun createSelectInsertShortcutAction(): ActionButtonWithText {
    val actions = arrayOf<AnAction>(
      @Suppress("HardCodedStringLiteral")
      InplaceChangeInlineCompletionShortcutAction(KeymapUtil.getKeyText(KeyEvent.VK_TAB), KeyboardShortcut.fromString("TAB")),
      @Suppress("HardCodedStringLiteral")
      InplaceChangeInlineCompletionShortcutAction(KeymapUtil.getKeyText(KeyEvent.VK_ENTER), KeyboardShortcut.fromString("ENTER")),
      ChangeToCustomInlineCompletionAction(),
    )
    val group = SelectShortcutActionGroup(actions)
    return object : ActionButtonWithText(group, group.templatePresentation.clone(), ActionPlaces.UNKNOWN, JBUI.emptySize()) {
      override fun getMargins(): Insets = JBUI.emptyInsets()
      override fun shallPaintDownArrow() = true
      override fun update() {
        presentation.text = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_INSERT_INLINE_COMPLETION)
      }
    }.also {
      it.isFocusable = false
      actions.filterIsInstance<InplaceChangeInlineCompletionShortcutAction>()
        .forEach { action -> action.updateAfterActionPerformed = it }
    }
  }
}