// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks.actions

import com.intellij.ide.bookmarks.BookmarkBundle.messagePointer
import com.intellij.ide.bookmarks.BookmarkType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal open class ToggleBookmarkTypeAction(
  private val type: BookmarkType
) : DumbAwareAction(messagePointer("bookmark.type.toggle.action.text", type.mnemonic)) {

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = event.dataContext.context != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    event.dataContext.context?.setType(type)
  }

  init {
    isEnabledInModalContext = true
  }
}

internal class ToggleBookmark1Action : ToggleBookmarkTypeAction(BookmarkType.DIGIT_1)
internal class ToggleBookmark2Action : ToggleBookmarkTypeAction(BookmarkType.DIGIT_2)
internal class ToggleBookmark3Action : ToggleBookmarkTypeAction(BookmarkType.DIGIT_3)
internal class ToggleBookmark4Action : ToggleBookmarkTypeAction(BookmarkType.DIGIT_4)
internal class ToggleBookmark5Action : ToggleBookmarkTypeAction(BookmarkType.DIGIT_5)
internal class ToggleBookmark6Action : ToggleBookmarkTypeAction(BookmarkType.DIGIT_6)
internal class ToggleBookmark7Action : ToggleBookmarkTypeAction(BookmarkType.DIGIT_7)
internal class ToggleBookmark8Action : ToggleBookmarkTypeAction(BookmarkType.DIGIT_8)
internal class ToggleBookmark9Action : ToggleBookmarkTypeAction(BookmarkType.DIGIT_9)
internal class ToggleBookmark0Action : ToggleBookmarkTypeAction(BookmarkType.DIGIT_0)

internal class ToggleBookmarkAAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_A)
internal class ToggleBookmarkBAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_B)
internal class ToggleBookmarkCAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_C)
internal class ToggleBookmarkDAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_D)
internal class ToggleBookmarkEAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_E)
internal class ToggleBookmarkFAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_F)
internal class ToggleBookmarkGAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_G)
internal class ToggleBookmarkHAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_H)
internal class ToggleBookmarkIAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_I)
internal class ToggleBookmarkJAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_J)
internal class ToggleBookmarkKAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_K)
internal class ToggleBookmarkLAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_L)
internal class ToggleBookmarkMAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_M)
internal class ToggleBookmarkNAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_N)
internal class ToggleBookmarkOAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_O)
internal class ToggleBookmarkPAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_P)
internal class ToggleBookmarkQAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_Q)
internal class ToggleBookmarkRAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_R)
internal class ToggleBookmarkSAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_S)
internal class ToggleBookmarkTAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_T)
internal class ToggleBookmarkUAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_U)
internal class ToggleBookmarkVAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_V)
internal class ToggleBookmarkWAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_W)
internal class ToggleBookmarkXAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_X)
internal class ToggleBookmarkYAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_Y)
internal class ToggleBookmarkZAction : ToggleBookmarkTypeAction(BookmarkType.LETTER_Z)
