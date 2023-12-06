// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.collaboration.messages.CollaborationToolsBundle
import org.jetbrains.annotations.Nls

enum class DiscussionsViewOption {
  ALL,
  UNRESOLVED_ONLY,
  DONT_SHOW
}

fun DiscussionsViewOption.toActionName(): @Nls String = when (this) {
  DiscussionsViewOption.ALL -> CollaborationToolsBundle.message("review.diff.discussions.view.option.all")
  DiscussionsViewOption.UNRESOLVED_ONLY -> CollaborationToolsBundle.message("review.diff.discussions.view.option.unresolved.only")
  DiscussionsViewOption.DONT_SHOW -> CollaborationToolsBundle.message("review.diff.discussions.view.option.do.not.show")
}