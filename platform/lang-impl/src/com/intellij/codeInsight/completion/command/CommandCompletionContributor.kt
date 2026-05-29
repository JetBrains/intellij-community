// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.command.configuration.ApplicationCommandCompletionService
import com.intellij.codeInsight.completion.group.GroupedCompletionContributor
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import org.jetbrains.annotations.Nls

internal class CommandCompletionContributor : CompletionContributor(), DumbAware, GroupedCompletionContributor {
  override fun groupIsEnabled(parameters: CompletionParameters): Boolean {
    try {
      val appService = ApplicationCommandCompletionService.getInstance()
      if (!appService.commandCompletionEnabled()) return false
      if (!appService.useGroupEnabled()) return false

      val commandCompletionService = parameters.position.project.service<CommandCompletionService>()
      val factory = commandCompletionService.getFactory(parameters.originalFile.language) ?: return false

      val commandType = findCommandCompletionType(
        factory = factory,
        isNonWritten = !parameters.originalFile.isWritable,
        offset = parameters.editor.caretModel.offset,
        editor = parameters.editor
      ) ?: return false

      return !(commandType is InvocationCommandType.FullSuffix && factory.supportFiltersWithDoublePrefix())
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      return false
    }
  }

  override fun getGroupDisplayName(): @Nls(capitalization = Nls.Capitalization.Title) String {
    return CodeInsightBundle.message("command.completion.title")
  }

  init {
    extend(CompletionType.BASIC,
           PlatformPatterns.psiElement(),
           CommandCompletionProvider(this))
  }
}