// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details

import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.setHtmlBody
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.util.text.DateFormatUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.swing.JComponent
import javax.swing.JEditorPane

object CodeReviewDetailsCommitInfoComponentFactory {
  private const val GAP = 8

  fun <T> create(
    scope: CoroutineScope,
    commit: Flow<T?>,
    commitPresenter: (T) -> CommitPresenter,
    htmlPaneFactory: () -> JEditorPane
  ): JComponent {
    val title = htmlPaneFactory()
    val info = htmlPaneFactory()

    scope.launch {
      commit.collect { commit: T? ->
        if (commit == null) {
          title.text = null
          info.text = null
          return@collect
        }

        val presentation = commitPresenter(commit)
        if (presentation is CommitPresenter.SingleCommit) {
          title.setHtmlBody(presentation.title)
          info.setHtmlBody("${presentation.author} ${DateFormatUtil.formatPrettyDateTime(presentation.committedDate)}")
        }
      }
    }

    return VerticalListPanel(GAP).apply {
      name = "Commit details info"
      bindVisibilityIn(scope, commit.map { it != null })

      add(title)
      add(info)
    }
  }
}