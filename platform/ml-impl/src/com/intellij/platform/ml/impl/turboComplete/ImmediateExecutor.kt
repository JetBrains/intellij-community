// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.turboComplete

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.addingPolicy.PassDirectlyPolicy
import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ImmediateExecutor(override val parameters: CompletionParameters,
                        override val policyController: PolicyController) : SuggestionGeneratorExecutor {
  override fun createNoneKindPolicy() = PassDirectlyPolicy()

  override fun executeAll() {}

  override fun pass(suggestionGenerator: SuggestionGenerator) {
    suggestionGenerator.generateCompletionVariants()
  }
}