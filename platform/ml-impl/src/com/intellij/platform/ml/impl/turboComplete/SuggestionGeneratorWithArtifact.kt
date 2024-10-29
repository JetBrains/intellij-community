// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.turboComplete

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.addingPolicy.PassDirectlyPolicy
import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.platform.ml.impl.turboComplete.addingPolicy.CollectionFillingPolicy
import org.jetbrains.annotations.ApiStatus

/**
 * Generates completion suggestions for a certain type of completion kind and stores
 * a cached artifact for later use.
 *
 * For example, we have two [SuggestionGenerator]s A and B.
 * While working, A generates an artifact, that is used later by B.
 * And the ML model decided, that B must be executed earlier.
 *
 * If we execute B first, then we want to preserve the recommended order of the
 * generators, and we don't want A to add completion variants to the lookup first.
 * Instead, A could be a [SuggestionGeneratorWithArtifact]. Then
 * 1. B asks A to create the artifact - [getArtifact]
 * 2. B generates completion variants
 *   (it's A's order now to generate variants)
 * 3. A will only collect put cached lookup elements to the result set
 */
@ApiStatus.Internal
abstract class SuggestionGeneratorWithArtifact<T>(override val kind: CompletionKind,
                                                  override val result: CompletionResultSet,
                                                  private val resultPolicyController: PolicyController,
                                                  override val parameters: CompletionParameters) : SuggestionGenerator {

  private var cachedArtifact: T? = null
  private var cachedLookupElements: MutableList<LookupElement>? = null

  fun getArtifact(): T {
    return cachedArtifact ?: run {
      val generatedLookupElements = mutableListOf<LookupElement>()
      val generatedArtifact = resultPolicyController.invokeWithPolicy(CollectionFillingPolicy(generatedLookupElements)) {
        generateVariantsAndArtifact()
      }
      cachedLookupElements = generatedLookupElements
      cachedArtifact = generatedArtifact
      generatedArtifact
    }
  }

  override fun generateCompletionVariants() {
    cachedLookupElements?.let {
      if (it.isEmpty()) return
      resultPolicyController.invokeWithPolicy(PassDirectlyPolicy()) {
        result.addAllElements(it)
      }
      it.clear()
    } ?: run {
      cachedArtifact = resultPolicyController.invokeWithPolicy(PassDirectlyPolicy()) {
        generateVariantsAndArtifact()
      }
    }
  }

  abstract fun generateVariantsAndArtifact(): T
}