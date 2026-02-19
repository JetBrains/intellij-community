// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.CompletionCommand

/**
 * An interface for defining logic to skip certain commands during the highlighting phase
 * in a code completion system.
 * It is used to mark command providers which can provide the same fixes as highlighting providers.
 * In this case, commands can be duplicated. To avoid this, this provider can be skipped.
 */
interface AfterHighlightingCommandProvider {
  fun skipCommandFromHighlighting(command: CompletionCommand): Boolean
}