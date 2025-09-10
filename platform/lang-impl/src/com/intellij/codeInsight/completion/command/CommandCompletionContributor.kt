// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.command.configuration.ApplicationCommandCompletionService
import com.intellij.codeInsight.completion.group.GroupedCompletionContributor
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
internal class CommandCompletionContributor : CompletionContributor(), DumbAware, GroupedCompletionContributor {
  override fun groupIsEnabled(parameters: CompletionParameters?): Boolean {
    try {
      if (!ApplicationCommandCompletionService.getInstance().commandCompletionEnabled()) return false
      if (!ApplicationCommandCompletionService.getInstance().useGroupEnabled()) return false
      val commandCompletionService = parameters?.editor?.project?.service<CommandCompletionService>() ?: return false
      val factory = commandCompletionService.getFactory(parameters.originalFile.language) ?: return false
      val supportFiltersWithDoublePrefix = factory.supportFiltersWithDoublePrefix()
      val commandType = findCommandCompletionType(factory, !parameters.originalFile.isWritable, parameters.offset, parameters.editor)
                        ?: return false
      if (commandType is InvocationCommandType.FullSuffix && supportFiltersWithDoublePrefix) return false
      return true
    }
    catch (_: Exception) {
      return false
    }
  }

  override fun getGroupDisplayName(): @Nls(capitalization = Nls.Capitalization.Title) String {
    return CodeInsightBundle.message("command.completion.title")
  }

  init {
    extend(CompletionType.BASIC,
           PlatformPatterns.psiElement(),
           CommandCompletionProvider())
  }
}