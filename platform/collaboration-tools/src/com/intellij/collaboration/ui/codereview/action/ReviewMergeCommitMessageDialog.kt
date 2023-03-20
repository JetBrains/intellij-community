// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JLabel

class ReviewMergeCommitMessageDialog(project: Project,
                                     title: @NlsContexts.DialogTitle String,
                                     subject: String,
                                     body: String) : DialogWrapper(project) {

  private val commitMessage = CommitMessage(project, false, false, true).apply {
    setCommitMessage("$subject\n\n$body")
    preferredSize = JBDimension(500, 85)
  }

  init {
    Disposer.register(disposable, commitMessage)

    setTitle(title)
    setOKButtonText(CollaborationToolsBundle.message("dialog.review.merge.commit.button.merge"))
    init()
  }

  override fun createCenterPanel(): JComponent {
    return JBUI.Panels.simplePanel(0, UIUtil.DEFAULT_VGAP)
      .addToTop(JLabel(CollaborationToolsBundle.message("dialog.review.merge.commit.message")))
      .addToCenter(commitMessage)
  }

  val message: String
    get() = commitMessage.comment
}