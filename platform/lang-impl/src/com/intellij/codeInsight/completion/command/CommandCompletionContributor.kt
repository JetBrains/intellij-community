// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.PlatformPatterns
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class CommandCompletionContributor : CompletionContributor(), DumbAware {
  init {
    extend(CompletionType.BASIC,
           PlatformPatterns.psiElement(),
           CommandCompletionProvider())
  }
}