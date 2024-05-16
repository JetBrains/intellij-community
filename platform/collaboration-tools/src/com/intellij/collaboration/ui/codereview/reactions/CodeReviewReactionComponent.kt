// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.reactions

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.util.CodeReviewColorUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBInsets
import icons.CollaborationToolsIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JComponent

@ApiStatus.Internal
object CodeReviewReactionComponent {
  fun createReactionButtonIn(cs: CoroutineScope, presentation: Flow<CodeReviewReactionPillPresentation>, toggle: () -> Unit): JComponent =
    PillButton().apply {
      isFocusable = false
      font = JBFont.create(font.deriveFont(CodeReviewReactionsUIUtil.COUNTER_FONT_SIZE))

      cs.launchNow {
        presentation.distinctUntilChanged().collect {
          icon = it.getIcon(CodeReviewReactionsUIUtil.ICON_SIZE)
          text = it.reactors.size.toString()
          if (it.isOwnReaction) {
            setBorderColor(CodeReviewColorUtil.Reaction.borderReacted)
            background = CodeReviewColorUtil.Reaction.backgroundReacted
          }
          else {
            setBorderColor()
            background = CodeReviewColorUtil.Reaction.background
          }
          toolTipText = CodeReviewReactionsUIUtil.createTooltipText(it.reactors, it.reactionName)
        }
      }.apply {
        addActionListener { toggle() }
      }
    }

  fun createNewReactionButton(showPicker: (JComponent) -> Unit): JComponent =
    PillButton().apply {
      isFocusable = false
      icon = CollaborationToolsIcons.AddEmoji
      margin = JBInsets.create(3, 6)
    }.also { btn ->
      btn.addActionListener {
        showPicker(btn)
      }
    }

  fun createPickReactionButton(emojiIcon: Icon, pick: () -> Unit): JComponent =
    PillButton().apply {
      isFocusable = false
      icon = emojiIcon
      addActionListener {
        pick()
      }
    }
}

interface CodeReviewReactionPillPresentation {
  val reactionName: @Nls String
  val reactors: List<@Nls String>
  val isOwnReaction: Boolean

  fun getIcon(size: Int): Icon
}
