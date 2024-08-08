// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.comment

import com.intellij.CommonBundle
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.ClippingRoundedPanel
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.timeline.thread.CodeReviewFoldableThreadViewModel
import com.intellij.collaboration.ui.codereview.timeline.thread.CodeReviewResolvableItemViewModel
import com.intellij.collaboration.ui.codereview.user.CodeReviewUser
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindDisabledIn
import com.intellij.collaboration.ui.util.bindIconIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.ui.JBColor
import com.intellij.ui.OverlaidOffsetIconsIcon
import com.intellij.ui.components.ActionLink
import com.intellij.util.containers.nullize
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.CollaborationToolsIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

object CodeReviewCommentUIUtil {

  const val INLAY_PADDING = 10
  private const val EDITOR_INLAY_PANEL_ARC = 10

  val COMMENT_BUBBLE_BORDER_COLOR: Color = JBColor.namedColor("Review.ChatItem.BubblePanel.Border",
                                                              JBColor.namedColor("EditorTabs.underTabsBorderColor",
                                                                          JBColor.border()))

  fun getInlayPadding(componentType: CodeReviewChatItemUIUtil.ComponentType): Insets {
    val paddingInsets = componentType.paddingInsets
    val top = INLAY_PADDING - paddingInsets.top
    val bottom = INLAY_PADDING - paddingInsets.bottom
    return JBInsets(top, 0, bottom, 0)
  }

  fun createEditorInlayPanel(component: JComponent): JPanel {
    val borderColor = JBColor.lazy {
      val scheme = EditorColorsManager.getInstance().globalScheme
      scheme.getColor(EditorColors.TEARLINE_COLOR) ?: JBColor.border()
    }
    val roundedPanel = ClippingRoundedPanel(EDITOR_INLAY_PANEL_ARC, borderColor, BorderLayout()).apply {
      background = JBColor.lazy {
        val scheme = EditorColorsManager.getInstance().globalScheme
        scheme.defaultBackground
      }
      add(UiDataProvider.wrapComponent(component) { sink ->
        suppressOuterEditorData(sink)
      })
    }
    component.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        roundedPanel.dispatchEvent(ComponentEvent(component, ComponentEvent.COMPONENT_RESIZED))
      }
    })
    return roundedPanel
  }

  private fun suppressOuterEditorData(sink: DataSink) {
    arrayOf(CommonDataKeys.EDITOR, CommonDataKeys.HOST_EDITOR, CommonDataKeys.EDITOR_EVEN_IF_INACTIVE,
            CommonDataKeys.CARET,
            CommonDataKeys.VIRTUAL_FILE, CommonDataKeys.VIRTUAL_FILE_ARRAY,
            CommonDataKeys.LANGUAGE,
            CommonDataKeys.PSI_FILE, CommonDataKeys.PSI_ELEMENT,
            PlatformCoreDataKeys.FILE_EDITOR,
            PlatformCoreDataKeys.PSI_ELEMENT_ARRAY).forEach {
      sink.setNull(it)
    }
  }

  fun createPostNowButton(actionListener: (ActionEvent) -> Unit): JComponent =
    ActionLink(CollaborationToolsBundle.message("review.comments.post-now.action")).apply {
      autoHideOnDisable = false
      addActionListener(actionListener)
    }

  fun createDeleteCommentIconButton(actionListener: (ActionEvent) -> Unit): JComponent {
    val icon = CollaborationToolsIcons.Delete
    val hoverIcon = CollaborationToolsIcons.DeleteHovered
    val button = InlineIconButton(icon, hoverIcon, tooltip = CommonBundle.message("button.delete"))
    button.actionListener = ActionListener {
      if (MessageDialogBuilder.yesNo(CollaborationToolsBundle.message("review.comments.delete.confirmation.title"),
                                     CollaborationToolsBundle.message("review.comments.delete.confirmation")).ask(button)) {
        actionListener(it)
      }
    }
    return button
  }

  fun createEditButton(actionListener: (ActionEvent) -> Unit): InlineIconButton {
    val icon = AllIcons.General.Inline_edit
    val hoverIcon = AllIcons.General.Inline_edit_hovered
    return InlineIconButton(icon, hoverIcon, tooltip = CommonBundle.message("button.edit")).apply {
      this.actionListener = ActionListener {
        actionListener(it)
      }
    }
  }

  fun createAddReactionButton(actionListener: (ActionEvent) -> Unit): InlineIconButton {
    val icon = CollaborationToolsIcons.AddEmoji
    val hoverIcon = CollaborationToolsIcons.AddEmojiHovered
    val button = InlineIconButton(icon, hoverIcon, tooltip = CollaborationToolsBundle.message("review.comments.reaction.add.tooltip"))
    button.actionListener = ActionListener {
      actionListener(it)
    }

    return button
  }

  fun createFoldedThreadControlsIn(cs: CoroutineScope,
                                   vm: CodeReviewFoldableThreadViewModel,
                                   avatarIconsProvider: IconsProvider<CodeReviewUser>): JComponent {
    val authorsLabel = JLabel().apply {
      bindVisibilityIn(cs, vm.repliesState.map { it.repliesCount > 0 })
      bindIconIn(cs, vm.repliesState.map {
        it.repliesAuthors.map {
          avatarIconsProvider.getIcon(it, CodeReviewChatItemUIUtil.ComponentType.COMPACT.iconSize)
        }.nullize()?.let {
          OverlaidOffsetIconsIcon(it)
        }
      })
    }
    val repliesLink = ActionLink("") { vm.unfoldReplies() }.apply {
      autoHideOnDisable = false
      isFocusPainted = false
      bindVisibilityIn(cs, vm.repliesState.combine(vm.canCreateReplies) { state, canReply -> state.repliesCount > 0 || canReply })
      bindDisabledIn(cs, vm.isBusy)
      bindTextIn(cs, vm.repliesState.map { state ->
        if (state.repliesCount == 0) {
          CollaborationToolsBundle.message("review.comments.reply.action")
        }
        else {
          CollaborationToolsBundle.message("review.comments.replies.action", state.repliesCount)
        }
      })
    }
    val lastReplyDateLabel = JLabel().apply {
      foreground = UIUtil.getContextHelpForeground()
      bindVisibilityIn(cs, vm.repliesState.map { it.lastReplyDate != null })
      bindTextIn(cs, vm.repliesState.map { it.lastReplyDate?.let { DateFormatUtil.formatPrettyDateTime(it) }.orEmpty() })
    }

    val repliesActions = HorizontalListPanel(CodeReviewTimelineUIUtil.Thread.Replies.ActionsFolded.HORIZONTAL_GAP).apply {
      add(authorsLabel)
      add(repliesLink)
      add(lastReplyDateLabel)
    }.also {
      CollaborationToolsUIUtil.hideWhenNoVisibleChildren(it)
    }
    val panel = HorizontalListPanel(CodeReviewTimelineUIUtil.Thread.Replies.ActionsFolded.HORIZONTAL_GROUP_GAP).apply {
      border = JBUI.Borders.empty(CodeReviewTimelineUIUtil.Thread.Replies.ActionsFolded.VERTICAL_PADDING, 0)
      add(repliesActions)
    }.also {
      CollaborationToolsUIUtil.hideWhenNoVisibleChildren(it)
    }

    if (vm is CodeReviewResolvableItemViewModel) {
      val unResolveLink = ActionLink("") { vm.changeResolvedState() }.apply {
        autoHideOnDisable = false
        isFocusPainted = false
        bindVisibilityIn(cs, vm.canChangeResolvedState)
        bindDisabledIn(cs, vm.isBusy)
        bindTextIn(cs, vm.isResolved.map { getResolveToggleActionText(it) })
      }
      panel.add(unResolveLink)
    }
    return panel
  }

  fun getResolveToggleActionText(resolved: Boolean) = if (resolved) {
    CollaborationToolsBundle.message("review.comments.unresolve.action")
  }
  else {
    CollaborationToolsBundle.message("review.comments.resolve.action")
  }

  object Title {
    const val HORIZONTAL_GAP = 8
    const val GROUP_HORIZONTAL_GAP = 12
  }

  object Actions {
    const val HORIZONTAL_GAP = 8
  }
}