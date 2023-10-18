// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.LightweightHint
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Point

internal object InlineCompletionTooltip {
  @RequiresEdt
  fun enterHover(session: InlineCompletionSession, locationAtScreen: Point) {
    val insertShortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_INSERT_INLINE_COMPLETION)
    val panel = HintUtil.createInformationLabel("Press $insertShortcut to accept")
    //val panel = panel {
    //  row {
    //    text("Press $insertShortcut to accept")
    //  }
    //}
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
  }
}