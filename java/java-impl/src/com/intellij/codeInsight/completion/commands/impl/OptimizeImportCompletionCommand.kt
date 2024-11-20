// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.idea.ActionsBundle

class OptimizeImportCompletionCommand : AbstractActionCompletionCommand("OptimizeImports",
                                                                        "Optimize imports",
                                                                        ActionsBundle.message("action.OptimizeImports.text"),
                                                                        null,
                                                                        -100)