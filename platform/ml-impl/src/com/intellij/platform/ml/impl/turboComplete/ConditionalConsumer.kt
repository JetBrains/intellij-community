// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.turboComplete

class ConditionalConsumer(
  base: SuggestionGeneratorConsumer,
  private val shouldExecuteKind: (SuggestionGenerator) -> Boolean,
) : DelegatingConsumer(base) {
  override fun pass(suggestionGenerator: SuggestionGenerator) {
    val generator = SuggestionGenerator.withApplicability(
      suggestionGenerator.kind,
      suggestionGenerator.result,
      suggestionGenerator.parameters,
      suggestionGenerator::generateCompletionVariants
    ) {
      shouldExecuteKind(suggestionGenerator)
    }

    super.pass(generator)
  }
}