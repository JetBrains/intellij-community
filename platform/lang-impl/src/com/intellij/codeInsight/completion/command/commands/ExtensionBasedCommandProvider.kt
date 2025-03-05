// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.ApplicableCompletionCommand
import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CommandProvider
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.registry.Registry

/**
 * An implementation of the CommandProvider interface that supplies completion commands
 * registered through extension points ('com.intellij.codeInsight.completion.applicable.command') for specific programming languages.
 */
internal class ExtensionPointCommandProvider : CommandProvider, DumbAware {
  override fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand> {
    if (!Registry.`is`("ide.completion.command.enabled")) return emptyList()
    val completionCommands =
      DumbService.getInstance(context.project)
        .filterByDumbAwareness(EP_NAME.allForLanguage(context.psiFile.language))
    return completionCommands.filter {
      !(context.isReadOnly && !it.supportsReadOnly()) && it.isApplicable(context.offset, context.psiFile, context.editor)
    }
  }

  override fun supportsReadOnly(): Boolean {
    return true
  }
}

private val EP_NAME: LanguageExtension<ApplicableCompletionCommand> = LanguageExtension<ApplicableCompletionCommand>("com.intellij.codeInsight.completion.applicable.command")
