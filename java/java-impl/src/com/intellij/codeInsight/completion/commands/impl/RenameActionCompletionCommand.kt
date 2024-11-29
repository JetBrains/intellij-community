// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.IdeActions

class RenameActionCompletionCommand : AbstractActionCompletionCommand(IdeActions.ACTION_RENAME,
                                                                      "rename",
                                                                      ActionsBundle.message("action.RenameElement.text"),
                                                                      null)