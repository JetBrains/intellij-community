// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.timeline.thread

import com.intellij.collaboration.ui.codereview.user.CodeReviewUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import java.util.*

interface CodeReviewFoldableThreadViewModel {
  val repliesState: StateFlow<RepliesStateData>
  val canCreateReplies: StateFlow<Boolean>
  val isBusy: StateFlow<Boolean>

  val repliesFolded: StateFlow<Boolean>

  fun unfoldReplies()

  interface RepliesStateData {
    val repliesAuthors: Set<CodeReviewUser>
    val repliesCount: Int
    val lastReplyDate: Date?

    @ApiStatus.Internal
    data class Default(override val repliesAuthors: Set<CodeReviewUser>,
                       override val repliesCount: Int,
                       override val lastReplyDate: Date?) : RepliesStateData

    object Empty : RepliesStateData {
      override val repliesAuthors: Set<CodeReviewUser> = emptySet()
      override val repliesCount: Int = 0
      override val lastReplyDate: Date? = null
    }
  }

}