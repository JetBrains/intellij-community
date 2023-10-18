// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.Disposer
import com.intellij.ui.LightweightHint
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Point
import javax.swing.JLabel
import javax.swing.JPanel

internal object InlineCompletionTooltip {
  @RequiresEdt
  fun enterHover(session: InlineCompletionSession, locationAtScreen: Point) {
    val insertShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_INSERT_INLINE_COMPLETION)
    val panel = JPanel().apply {
      add(JLabel(IdeBundle.message("inline.completion.tooltip.shortcuts", insertShortcut)))
      add(session.provider.getTooltip())
    }
    panel.background = HintUtil.getInformationColor()
    val hint = LightweightHint(panel)
    val editor = session.context.editor

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