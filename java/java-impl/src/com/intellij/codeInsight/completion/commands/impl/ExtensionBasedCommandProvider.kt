// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.commands.api.ApplicableCompletionCommand
import com.intellij.codeInsight.completion.commands.api.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.commands.api.CommandProvider
import com.intellij.codeInsight.completion.commands.api.CompletionCommand
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService


class ExtensionPointCommandProvider : CommandProvider, DumbAware {
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    val completionCommands = DumbService.getDumbAwareExtensions(context.project, ApplicableCompletionCommand.EP_NAME)
    return completionCommands.filter {
      !(context.isReadOnly && !it.supportsReadOnly()) && it.isApplicable(context.offset, context.psiFile, context.editor)
    }
  }

  override fun getId(): String {
    return "ExtensionPointCommandProvider"
  }

  override fun supportsReadOnly(): Boolean {
    return true
  }
}