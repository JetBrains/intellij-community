// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.project.DumbAwareAction

abstract class CodeReviewCheckoutRemoteBranchAction : DumbAwareAction(
  CollaborationToolsBundle.message("review.details.action.branch.checkout.remote.action"),
  CollaborationToolsBundle.message("review.details.action.branch.checkout.remote.action.description"),
  null
)