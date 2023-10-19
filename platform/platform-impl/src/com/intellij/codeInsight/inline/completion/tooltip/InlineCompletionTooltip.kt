// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.Disposer
import com.intellij.ui.LightweightHint
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.util.preferredHeight
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI

internal object InlineCompletionTooltip {
  @RequiresEdt
  fun enterHover(session: InlineCompletionSession) {


    val editor = session.context.editor
    val activeLookup = LookupManager.getActiveLookup(editor)

    if (activeLookup?.isPositionedAboveCaret == true) {
      return
    }

    val insertShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_INSERT_INLINE_COMPLETION)
    val panel = panel {
      row {
        link(insertShortcut) {
          // TODO refactor: direct action call
          ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
              CommandProcessor.getInstance().executeCommand(editor.project, {
                InlineCompletion.getHandlerOrNull(editor)?.insert()
              }, null, null, editor.document)
            }
          }
        }.gap(RightGap.SMALL)
        @Suppress("DialogTitleCapitalization")
        text(IdeBundle.message("inline.completion.tooltip.shortcuts.accept.description")).gap(RightGap.SMALL)
        cell(session.provider.getTooltip(editor.project))
      }
    }.apply {
      border = JBUI.Borders.empty(4)
    }

    val hint = LightweightHint(panel).apply {
      setForceShowAsPopup(true)
    }

    val pos = editor.offsetToLogicalPosition(editor.caretModel.offset)
    val location = HintManagerImpl.getHintPosition(hint, editor, pos, HintManager.ABOVE)
    location.y -= panel.preferredHeight

    HintManagerImpl.getInstanceImpl().showEditorHint(
      hint,
      editor,
      location,
      HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_TEXT_CHANGE or HintManager.HIDE_BY_SCROLLING,
      0,
      false,
      HintManagerImpl.createHintHint(editor, location, hint, HintManager.ABOVE).setContentActive(false)
    )
    Disposer.register(session) {
      hint.hide()
    }
  }
}