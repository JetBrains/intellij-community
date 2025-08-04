// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.CompletionCommand

interface HighlightCommandSkipper {
  fun skipForHighlightCommand(command: CompletionCommand): Boolean
}