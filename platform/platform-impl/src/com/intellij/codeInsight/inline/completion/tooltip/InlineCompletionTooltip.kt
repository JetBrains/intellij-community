// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.Disposer
import com.intellij.ui.LightweightHint
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Point

internal object InlineCompletionTooltip {
  @RequiresEdt
  fun enterHover(session: InlineCompletionSession, locationAtScreen: Point) {
    val editor = session.context.editor

    val insertShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_INSERT_INLINE_COMPLETION)
    val panel = panel {
      row {
        link(insertShortcut) {
          ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
              CommandProcessor.getInstance().executeCommand(editor.project, {
                InlineCompletion.getHandlerOrNull(editor)?.insert()
              }, null, null, editor.document)
            }
          }
        }.gap(RightGap.SMALL)
        text(IdeBundle.message("inline.completion.tooltip.shortcuts.accept.description")).gap(RightGap.SMALL)
        cell(session.provider.getTooltip(editor.project))
      }
    }
    val hint = LightweightHint(panel)

    val editorLocation = editor.contentComponent.topLevelAncestor.locationOnScreen
    val point = Point(locationAtScreen.x - editorLocation.x, locationAtScreen.y - editorLocation.y)

    HintManagerImpl.getInstanceImpl().showEditorHint(
      hint,
      editor,
      point,
      HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_TEXT_CHANGE or HintManager.HIDE_BY_SCROLLING,
      0,
      false,
      HintManagerImpl.createHintHint(editor, point, hint, HintManager.ABOVE).setContentActive(false)
    )
    Disposer.register(session) {
      hint.hide()
    }
  }
}