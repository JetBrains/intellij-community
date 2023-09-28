// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.turboComplete

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.addingPolicy.PolicyController
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SuggestionGeneratorExecutorProvider {
  fun shouldBeCalled(parameters: CompletionParameters): Boolean

  fun createExecutor(
    parameters: CompletionParameters,
    policyController: PolicyController,
  ): SuggestionGeneratorExecutor

  companion object {
    private val EP_NAME: ExtensionPointName<SuggestionGeneratorExecutorProvider> =
      ExtensionPointName("com.intellij.turboComplete.suggestionGeneratorExecutorProvider")

    fun hasAnyToCall(parameters: CompletionParameters): Boolean {
      return EP_NAME.extensionList.any { it.shouldBeCalled(parameters) }
    }

    fun findOneMatching(parameters: CompletionParameters): SuggestionGeneratorExecutorProvider {
      val allExecutorProviders = EP_NAME.extensionList.filter { it.shouldBeCalled(parameters) }
      if (allExecutorProviders.size > 1) {
        throw IllegalStateException(
          "Found more than one matching CompletionKindExecutorProvider: ${allExecutorProviders.map { it.javaClass.name }}")
      }
      return allExecutorProviders[0]
    }
  }
}