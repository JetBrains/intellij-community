// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks.actions

import com.intellij.ide.bookmarks.BookmarkBundle.messagePointer
import com.intellij.ide.bookmarks.BookmarkManager
import com.intellij.ide.bookmarks.BookmarkType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal open class GotoBookmarkTypeAction(
  private val type: BookmarkType
) : DumbAwareAction(messagePointer("goto.bookmark.type.action.text", type.mnemonic)) {

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = getNavigatableBookmark(event) != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    getNavigatableBookmark(event)?.navigate(true)
  }

  private fun getNavigatableBookmark(event: AnActionEvent) = event.project
    ?.let { BookmarkManager.getInstance(it) }
    ?.findBookmarkForMnemonic(type.mnemonic)
    ?.let { if (it.canNavigate()) it else null }

  init {
    isEnabledInModalContext = true
  }
}

internal class GotoBookmark1Action : GotoBookmarkTypeAction(BookmarkType.DIGIT_1)
internal class GotoBookmark2Action : GotoBookmarkTypeAction(BookmarkType.DIGIT_2)
internal class GotoBookmark3Action : GotoBookmarkTypeAction(BookmarkType.DIGIT_3)
internal class GotoBookmark4Action : GotoBookmarkTypeAction(BookmarkType.DIGIT_4)
internal class GotoBookmark5Action : GotoBookmarkTypeAction(BookmarkType.DIGIT_5)
internal class GotoBookmark6Action : GotoBookmarkTypeAction(BookmarkType.DIGIT_6)
internal class GotoBookmark7Action : GotoBookmarkTypeAction(BookmarkType.DIGIT_7)
internal class GotoBookmark8Action : GotoBookmarkTypeAction(BookmarkType.DIGIT_8)
internal class GotoBookmark9Action : GotoBookmarkTypeAction(BookmarkType.DIGIT_9)
internal class GotoBookmark0Action : GotoBookmarkTypeAction(BookmarkType.DIGIT_0)

internal class GotoBookmarkAAction : GotoBookmarkTypeAction(BookmarkType.LETTER_A)
internal class GotoBookmarkBAction : GotoBookmarkTypeAction(BookmarkType.LETTER_B)
internal class GotoBookmarkCAction : GotoBookmarkTypeAction(BookmarkType.LETTER_C)
internal class GotoBookmarkDAction : GotoBookmarkTypeAction(BookmarkType.LETTER_D)
internal class GotoBookmarkEAction : GotoBookmarkTypeAction(BookmarkType.LETTER_E)
internal class GotoBookmarkFAction : GotoBookmarkTypeAction(BookmarkType.LETTER_F)
internal class GotoBookmarkGAction : GotoBookmarkTypeAction(BookmarkType.LETTER_G)
internal class GotoBookmarkHAction : GotoBookmarkTypeAction(BookmarkType.LETTER_H)
internal class GotoBookmarkIAction : GotoBookmarkTypeAction(BookmarkType.LETTER_I)
internal class GotoBookmarkJAction : GotoBookmarkTypeAction(BookmarkType.LETTER_J)
internal class GotoBookmarkKAction : GotoBookmarkTypeAction(BookmarkType.LETTER_K)
internal class GotoBookmarkLAction : GotoBookmarkTypeAction(BookmarkType.LETTER_L)
internal class GotoBookmarkMAction : GotoBookmarkTypeAction(BookmarkType.LETTER_M)
internal class GotoBookmarkNAction : GotoBookmarkTypeAction(BookmarkType.LETTER_N)
internal class GotoBookmarkOAction : GotoBookmarkTypeAction(BookmarkType.LETTER_O)
internal class GotoBookmarkPAction : GotoBookmarkTypeAction(BookmarkType.LETTER_P)
internal class GotoBookmarkQAction : GotoBookmarkTypeAction(BookmarkType.LETTER_Q)
internal class GotoBookmarkRAction : GotoBookmarkTypeAction(BookmarkType.LETTER_R)
internal class GotoBookmarkSAction : GotoBookmarkTypeAction(BookmarkType.LETTER_S)
internal class GotoBookmarkTAction : GotoBookmarkTypeAction(BookmarkType.LETTER_T)
internal class GotoBookmarkUAction : GotoBookmarkTypeAction(BookmarkType.LETTER_U)
internal class GotoBookmarkVAction : GotoBookmarkTypeAction(BookmarkType.LETTER_V)
internal class GotoBookmarkWAction : GotoBookmarkTypeAction(BookmarkType.LETTER_W)
internal class GotoBookmarkXAction : GotoBookmarkTypeAction(BookmarkType.LETTER_X)
internal class GotoBookmarkYAction : GotoBookmarkTypeAction(BookmarkType.LETTER_Y)
internal class GotoBookmarkZAction : GotoBookmarkTypeAction(BookmarkType.LETTER_Z)
