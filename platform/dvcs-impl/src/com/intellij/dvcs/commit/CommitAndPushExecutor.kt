// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.commit

import com.intellij.dvcs.ui.DvcsBundle.message
import com.intellij.openapi.util.NlsActions
import com.intellij.vcs.commit.CommitWorkflowHandler
import com.intellij.vcs.commit.NonModalCommitWorkflowHandler

@NlsActions.ActionText
fun CommitWorkflowHandler.getCommitAndPushActionName(): String {
  val isAmend = amendCommitHandler.isAmendCommitMode
  val isSkipCommitChecks = (this as? NonModalCommitWorkflowHandler<*, *>)?.isSkipCommitChecks() == true

  return when {
    isAmend && isSkipCommitChecks -> message("action.amend.commit.anyway.and.push.text")
    isAmend && !isSkipCommitChecks -> message("action.amend.commit.and.push.text")
    !isAmend && isSkipCommitChecks -> message("action.commit.anyway.and.push.text")
    else -> message("action.commit.and.push.text")
  }
}