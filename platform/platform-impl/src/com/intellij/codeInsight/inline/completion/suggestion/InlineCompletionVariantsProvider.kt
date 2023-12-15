// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.suggestion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariantState.Status
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

internal interface InlineCompletionVariantsProvider : Disposable {

  @RequiresEdt
  fun currentVariant(): InlineCompletionPresentableVariant

  @RequiresEdt
  fun nextVariant(): InlineCompletionPresentableVariant

  @RequiresEdt
  fun prevVariant(): InlineCompletionPresentableVariant

  @RequiresEdt
  fun getVariantsNumber(): Int

  @RequiresEdt
  fun estimateNonEmptyVariantsNumber(): IntRange
}

internal abstract class InlineCompletionVariantsComputer @RequiresEdt constructor(
  variants: List<InlineCompletionSuggestion.Variant>
) : InlineCompletionVariantsProvider {

  init {
    check(variants.isNotEmpty())
  }

  private val variantsStates = List(variants.size) {
    InlineCompletionVariantState(mutableListOf(), variants[it].data)
  }

  private var currentVariant: InlineCompletionPresentableVariantImpl = getVariant(0)

  override fun currentVariant(): InlineCompletionPresentableVariant {
    ThreadingAssertions.assertEventDispatchThread()
    return currentVariant
  }

  override fun nextVariant(): InlineCompletionPresentableVariant {
    ThreadingAssertions.assertEventDispatchThread()
    disposeCurrentVariant()
    val index = currentVariant.index
    val nextIndex = findStateIndex(index + 1, false) { isPossibleToShow(it) } ?: return currentVariant
    return useVariant(nextIndex)
  }

  override fun prevVariant(): InlineCompletionPresentableVariant {
    ThreadingAssertions.assertEventDispatchThread()
    disposeCurrentVariant()
    val index = currentVariant.index
    val prevIndex = findStateIndex(index - 1, true) { isPossibleToShow(it) } ?: return currentVariant
    return useVariant(prevIndex)
  }

  override fun getVariantsNumber(): Int {
    return variantsStates.size
  }

  override fun estimateNonEmptyVariantsNumber(): IntRange {
    ThreadingAssertions.assertEventDispatchThread()

    var nonEmptyResults = 0
    var potentiallyNonEmptyResults = 0

    for (state in variantsStates) {
      when (state.status) {
        Status.NOT_STARTED, Status.IN_PROGRESS_EMPTY -> {
          potentiallyNonEmptyResults++
        }
        Status.IN_PROGRESS_NON_EMPTY, Status.COMPLETED_NON_EMPTY -> {
          potentiallyNonEmptyResults++
          nonEmptyResults++
        }
        Status.COMPLETED_EMPTY -> Unit
      }
    }

    return IntRange(nonEmptyResults, potentiallyNonEmptyResults)
  }

  override fun dispose() {
    disposeCurrentVariant()
  }

  protected abstract fun elementProposed(variantIndex: Int, elementIndex: Int, element: InlineCompletionElement)

  // TODO add direction
  protected abstract fun beforeVariantChanged(variantIndex: Int)

  @RequiresEdt
  protected fun elementComputed(variantIndex: Int, elementIndex: Int, element: InlineCompletionElement) {
    ThreadingAssertions.assertEventDispatchThread()
    validateIndex(variantIndex)

    val state = variantsStates[variantIndex]
    state.elements += element
    state.status = Status.IN_PROGRESS_NON_EMPTY
    if (currentVariant.index == variantIndex) {
      currentVariant.addElement(element, elementIndex)
    }
  }

  @RequiresEdt
  protected fun variantComputed(variantIndex: Int) {
    ThreadingAssertions.assertEventDispatchThread()
    validateIndex(variantIndex)

    val state = variantsStates[variantIndex]
    state.status = when (state.status) {
      Status.IN_PROGRESS_EMPTY -> Status.COMPLETED_EMPTY
      Status.IN_PROGRESS_NON_EMPTY -> Status.COMPLETED_NON_EMPTY
      else -> error("Incorrect state of variant status.")
    }
  }

  @RequiresEdt
  protected fun variantStartedComputing(variantIndex: Int) {
    ThreadingAssertions.assertEventDispatchThread()
    validateIndex(variantIndex)
    variantsStates[variantIndex].status = Status.IN_PROGRESS_EMPTY
  }

  @RequiresEdt
  private fun getVariant(index: Int): InlineCompletionPresentableVariantImpl {
    ThreadingAssertions.assertEventDispatchThread()
    check(isPossibleToShow(index))

    val state = variantsStates[index]
    val computedVariant = InlineCompletionPresentableVariantImpl(state.data, index) { element, elementIndex ->
      elementProposed(variantIndex = index, elementIndex = elementIndex, element = element)
    }
    state.elements.forEachIndexed { elementIndex, element -> computedVariant.addElement(element, elementIndex) }
    return computedVariant
  }

  @RequiresEdt
  private fun useVariant(index: Int): InlineCompletionPresentableVariantImpl {
    beforeVariantChanged(index)
    return getVariant(index).also {
      currentVariant = it
    }
  }

  private fun isPossibleToShow(index: Int): Boolean {
    validateIndex(index)
    val state = variantsStates[index]
    // TODO not 0
    return (index == 0 && !state.status.isCompleted()) || state.status.isPossibleToShow()
  }

  private fun validateIndex(index: Int) {
    check(index in variantsStates.indices)
  }

  private fun disposeCurrentVariant() {
    Disposer.dispose(currentVariant)
  }

  private inline fun findStateIndex(
    startIndex: Int,
    reversed: Boolean = false,
    predicate: (Int) -> Boolean
  ): Int? {
    val delta = if (reversed) -1 else 1
    var index = startIndex
    do {
      index = (index + variantsStates.size) % variantsStates.size
      if (predicate(index)) {
        return index
      }
      index += delta
    } while (index != startIndex)

    return null
  }
}

private class InlineCompletionPresentableVariantImpl(
  override val data: UserDataHolderBase,
  override val index: Int,
  private val onAdd: (InlineCompletionElement, Int) -> Unit
) : InlineCompletionPresentableVariant {

  private val channel = Channel<InlineCompletionElement>(capacity = Channel.UNLIMITED)

  override val elements: Flow<InlineCompletionElement> = channel.consumeAsFlow()

  @RequiresEdt
  fun addElement(element: InlineCompletionElement, elementIndex: Int) {
    check(channel.trySend(element).isSuccess)
    onAdd(element, elementIndex)
  }

  override fun dispose() {
    channel.close()
  }
}

private class InlineCompletionVariantState(
  val elements: MutableList<InlineCompletionElement>,
  val data: UserDataHolderBase
) {

  var status: Status = Status.NOT_STARTED

  enum class Status {
    NOT_STARTED,
    IN_PROGRESS_EMPTY,
    IN_PROGRESS_NON_EMPTY,
    COMPLETED_EMPTY,
    COMPLETED_NON_EMPTY
  }
}

private fun Status.isPossibleToShow(): Boolean {
  return this == Status.IN_PROGRESS_NON_EMPTY || this == Status.COMPLETED_NON_EMPTY
}

private fun Status.isCompleted(): Boolean {
  return this == Status.COMPLETED_NON_EMPTY || this == Status.COMPLETED_EMPTY
}
