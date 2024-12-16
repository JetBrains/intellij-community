// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.codeInsight.inline.completion.suggestion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

@DslMarker
private annotation class InlineCompletionSuggestionDslMarker

/**
 * Used to conveniently build [InlineCompletionSuggestion].
 * This builder is thread-safe, meaning that you can concurrently add variants.
 *
 * Example:
 * ```kotlin
 * return InlineCompletionSuggestion {
 *  variant { data ->
 *    data.putUserData(...)
 *    emit(...)
 *    emit(...)
 *  }
 *  variant {
 *    emit(...)
 *  }
 * }
 * ```
 *
 * @see InlineCompletionSuggestion.Companion.invoke
 */
sealed interface InlineCompletionSuggestionBuilder {

  @InlineCompletionSuggestionDslMarker
  suspend fun variant(
    data: UserDataHolderBase = UserDataHolderBase(),
    buildElements: suspend FlowCollector<InlineCompletionElement>.(data: UserDataHolderBase) -> Unit
  )
}

private class InlineCompletionSuggestionBuilderImpl : InlineCompletionSuggestionBuilder {

  private val isBuilt = AtomicBoolean(false)
  private val variants = ContainerUtil.createConcurrentList<InlineCompletionVariant>()

  override suspend fun variant(
    data: UserDataHolderBase,
    buildElements: suspend FlowCollector<InlineCompletionElement>.(data: UserDataHolderBase) -> Unit
  ) {
    check(!isBuilt.get()) {
      "Cannot add another variant after a suggestion is already built. Incorrect API usage."
    }
    variants += InlineCompletionVariant.build(data, buildElements)
  }

  fun build(): InlineCompletionSuggestion {
    isBuilt.set(true)
    return object : InlineCompletionSuggestion {
      override suspend fun getVariants(): List<InlineCompletionVariant> = variants
    }
  }
}

suspend operator fun InlineCompletionSuggestion.Companion.invoke(
  block: suspend InlineCompletionSuggestionBuilder.() -> Unit
): InlineCompletionSuggestion {
  return InlineCompletionSuggestionBuilderImpl().apply { block() }.build()
}
