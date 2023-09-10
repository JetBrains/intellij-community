// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.onboarding

import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.inline.completion.InlineCompletionProvider
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.util.asSafely
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
private class ResetICOnboardingAtFirstShowAction : AResetICOnboardingAction(
  { editor, caret, context -> InlineCompletionTooltipBuilder.atFirstShow(editor, caret) }
)

@ApiStatus.Experimental
private class ResetICOnboardingAtFirstAcceptAction : AResetICOnboardingAction(
  { editor, caret, context -> InlineCompletionTooltipBuilder.atFirstAccept(editor, caret, context) }
)

@ApiStatus.Experimental
private class ResetICOnboardingShowSettingsAction : AResetICOnboardingAction(
  { editor, caret, context -> InlineCompletionTooltipBuilder.showSettings(editor) }
)

@ApiStatus.Experimental
private abstract class AResetICOnboardingAction(
  act: (editor: EditorImpl, caret: Caret, dataContext: DataContext?) -> Unit
) : EditorAction(CallInlineCompletionHandler(act)), HintManagerImpl.ActionToIgnore {
  class CallInlineCompletionHandler(val act: (editor: EditorImpl, caret: Caret, dataContext: DataContext?) -> Unit) : EditorWriteActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
      if (editor !is EditorImpl) return
      val curCaret = caret ?: editor.caretModel.currentCaret

      act(editor, curCaret, dataContext)
    }
  }
}

private class ChangeToTabInlineCompletionAction : AnAction("Tab", "Tab to Insert", null), DumbAware, LightEditCompatible {
  override fun actionPerformed(e: AnActionEvent) {
    ActionManager.getInstance().getAction("InsertInlineCompletionAction")
      .registerCustomShortcutSet(CustomShortcutSet(KeyboardShortcut.fromString("tab")), null)
  }
}

private class ChangeToEnterInlineCompletionAction : AnAction("Enter", "Enter to Insert", null), DumbAware, LightEditCompatible {
  override fun actionPerformed(e: AnActionEvent) {
    ActionManager.getInstance().getAction("InsertInlineCompletionAction")
      .registerCustomShortcutSet(CustomShortcutSet(KeyboardShortcut.fromString("enter")), null)
  }
}

private class ChangeToCustomInlineCompletionAction : AnAction("Custom..."), DumbAware, LightEditCompatible {
  override fun actionPerformed(e: AnActionEvent) {
    ShowSettingsUtil.getInstance().showSettingsDialog(e.project, KeymapPanel::class.java)
  }
}

fun addInlineCompletionBehaviourActions(group: DefaultActionGroup) {
  // Do not add actions if inline completion does not exist
  if (InlineCompletionProvider.extensions().isEmpty()) {
    return
  }

  group.addSeparator("Inline Completion")

  group.add(
    DefaultActionGroup("Custom...", true).apply {
      addAll(
        ChangeToTabInlineCompletionAction(),
        ChangeToEnterInlineCompletionAction(),
        ChangeToCustomInlineCompletionAction(),
      )
    }
  )

  ActionManager.getInstance().getAction("InlineCompletion.Settings").asSafely<DefaultActionGroup>()
    ?.apply { isPopup = false }
    ?.let(group::add)
}

