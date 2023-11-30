package com.intellij.platform.ml.impl.turboComplete

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.addingPolicy.ElementsAddingPolicy
import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SuggestionGeneratorExecutor : SuggestionGeneratorConsumer {
  val parameters: CompletionParameters
  val policyController: PolicyController

  fun createNoneKindPolicy(): ElementsAddingPolicy

  fun executeAll()
}