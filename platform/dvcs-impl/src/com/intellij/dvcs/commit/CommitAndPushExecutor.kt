// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.commit

import com.intellij.dvcs.ui.DvcsBundle.message
import com.intellij.openapi.util.NlsActions
import com.intellij.vcs.commit.CommitWorkflowHandler
import com.intellij.vcs.commit.CommitWorkflowHandlerState

@NlsActions.ActionText
fun CommitWorkflowHandler.getCommitAndPushActionName(): String {
  return getCommitAndPushActionName(getState())
}

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