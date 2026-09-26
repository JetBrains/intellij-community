// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.commit

import com.intellij.dvcs.ui.DvcsBundle.message
import com.intellij.openapi.util.NlsActions
import com.intellij.vcs.commit.CommitWorkflowHandlerState

@NlsActions.ActionText
fun getCommitAndPushActionName(state: CommitWorkflowHandlerState): String {
  val isAmend = state.isAmend
  val isSkipCommitChecks = state.isSkipCommitChecks

  return when {
    isAmend && isSkipCommitChecks -> message("action.amend.commit.anyway.and.push.text")
    isAmend && !isSkipCommitChecks -> message("action.amend.commit.and.push.text")
    !isAmend && isSkipCommitChecks -> message("action.commit.anyway.and.push.text")
    else -> message("action.commit.and.push.text")
  }
}
