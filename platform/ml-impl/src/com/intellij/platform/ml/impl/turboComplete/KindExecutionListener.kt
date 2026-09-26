// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.turboComplete

import com.intellij.codeInsight.completion.CompletionParameters
import org.jetbrains.annotations.ApiStatus

/**
 * Listens how [SuggestionGenerator]s are executed
 * Be aware, that each listener is not reinitialized, but
 * the same instance used to convey information about generators'
 * execution each time.
 *
 * The functions are called in their declaration order.
 */
@ApiStatus.Internal
interface KindExecutionListener {
  /**
   * Code completion was just called
   */
  fun onInitialize(parameters: CompletionParameters) {}

  /**
   * Called before any [KindCollector] has collected any kinds,
   * so the collection process just began
   */
  fun onCollectionStarted() {}

  /**
   * A [SuggestionGenerator] had been collected.
   * There could be multiple suggestion generators, hence,
   * the function is called as many times.
   */
  fun onGeneratorCollected(suggestionGenerator: SuggestionGenerator) {}

  /**
   * All kinds have been collected
   */
  fun onCollectionFinished() {}

  /**
   * The generator started generating variants,
   * i.e. [SuggestionGenerator.generateCompletionVariants] was called
   */
  fun onGenerationStarted(suggestionGenerator: SuggestionGenerator) {}

  /**
   * The generator finished generating variants,
   * i.e. [SuggestionGenerator.generateCompletionVariants] finished
   * (either by an exception, or without)
   */
  fun onGenerationFinished(suggestionGenerator: SuggestionGenerator) {}
}