// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.commit

import com.intellij.platform.vcs.impl.shared.commit.EditedCommitDetails
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsUser
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
fun EditedCommitDetails.Companion.create(currentUser: VcsUser?, commit: VcsFullCommitDetails): EditedCommitDetails = EditedCommitDetails(
  currentUser = currentUser,
  committer = commit.committer,
  author = commit.author,
  commitHash = commit.id,
  subject = commit.subject,
  fullMessage = commit.fullMessage,
  changes = commit.changes
)
