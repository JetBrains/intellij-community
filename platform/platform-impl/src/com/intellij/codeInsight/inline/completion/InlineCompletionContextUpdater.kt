// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import org.jetbrains.annotations.Contract

internal abstract class InlineCompletionContextUpdater {

  @Contract(pure = true)
  abstract fun onEvent(context: InlineCompletionContext, event: InlineCompletionEvent): Result

  sealed interface Result {
    sealed interface Defined : Result

    sealed interface Updated : Defined {
      class Changed(val newElements: List<InlineCompletionElement>) : Updated

      object Same : Updated
    }

    object Invalidated : Defined

    object Undefined : Result
  }
}

internal abstract class DefinedInlineCompletionContextUpdater : InlineCompletionContextUpdater() {
  abstract override fun onEvent(context: InlineCompletionContext, event: InlineCompletionEvent): Result.Defined
}

internal fun InlineCompletionContextUpdater.invalidateOnUndefined(): DefinedInlineCompletionContextUpdater {
  return object : DefinedInlineCompletionContextUpdater() {
    override fun onEvent(context: InlineCompletionContext, event: InlineCompletionEvent): Result.Defined {
      return when (val result = this@invalidateOnUndefined.onEvent(context, event)) {
        is Result.Defined -> result
        is Result.Undefined -> Result.Invalidated
      }
    }
  }
}

internal class AppendPrefixContextUpdater : InlineCompletionContextUpdater() {
  override fun onEvent(context: InlineCompletionContext, event: InlineCompletionEvent): Result {
    return when (event) {
      is InlineCompletionEvent.DocumentChange -> {
        val fragment = event.getFragmentToAppendPrefix()
        val newElements = fragment?.let { applyPrefixAppend(context, it) }
        if (newElements != null) Result.Updated.Changed(newElements) else Result.Invalidated
      }
      else -> Result.Undefined
    }
  }

  private fun applyPrefixAppend(context: InlineCompletionContext, fragment: CharSequence): List<InlineCompletionElement>? {
    // only one symbol is permitted
    if (fragment.length != 1 || !context.lineToInsert.startsWith(fragment) || context.lineToInsert == fragment.toString()) {
      return null
    }
    return truncateElementsPrefix(context.state.elements, fragment.length)
  }

  private fun InlineCompletionEvent.DocumentChange.getFragmentToAppendPrefix(): String? {
    val newFragment = event.newFragment
    val oldFragment = event.oldFragment
    return newFragment.takeIf { it.isNotEmpty() && oldFragment.isEmpty() }?.toString()
  }

  private fun truncateElementsPrefix(elements: List<InlineCompletionElement>, length: Int): List<InlineCompletionElement> {
    var currentLength = length
    val newFirstElementIndex = elements.indexOfFirst {
      currentLength -= it.text.length
      currentLength < 0 // Searching for the element that exceeds [length]
    }
    assert(newFirstElementIndex >= 0)
    currentLength += elements[newFirstElementIndex].text.length
    val newFirstElement = InlineCompletionElement(elements[newFirstElementIndex].text.drop(currentLength))
    return listOf(newFirstElement) + elements.drop(newFirstElementIndex + 1)
  }
}

internal class LookupChangeContextUpdater(private val providerExists: Boolean) : InlineCompletionContextUpdater() {
  override fun onEvent(context: InlineCompletionContext, event: InlineCompletionEvent): Result {
    return when (event) {
      is InlineCompletionEvent.LookupChange -> {
        if (providerExists || !context.isCurrentlyDisplayingInlays) Result.Invalidated else Result.Updated.Same
      }
      else -> Result.Undefined
    }
  }
}

internal class InvalidateIfNoProviderContextUpdater(private val providerExists: Boolean) : InlineCompletionContextUpdater() {
  override fun onEvent(context: InlineCompletionContext, event: InlineCompletionEvent): Result {
    return if (providerExists) Result.Undefined else Result.Invalidated
  }
}

/**
 * Returns first [InlineCompletionContextUpdater.Result.Defined] from [updaters] if found,
 * otherwise [InlineCompletionContextUpdater.Result.Undefined] is returned.
 */
internal class CompositeContextUpdater(private vararg val updaters: InlineCompletionContextUpdater) : InlineCompletionContextUpdater() {
  override fun onEvent(context: InlineCompletionContext, event: InlineCompletionEvent): Result {
    return updaters.firstNotNullOfOrNull { it.onEvent(context, event) as? Result.Defined } ?: Result.Undefined
  }
}
