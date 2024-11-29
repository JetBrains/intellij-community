// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.core

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
private class CommandCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC,
           PlatformPatterns.psiElement(),
           CommandCompletionProvider())
  }
}