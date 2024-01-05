// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.suggestion

import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariantState.Status
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt

internal interface InlineCompletionVariantsProvider : Disposable {

  @RequiresEdt // TODO maybe we do not need that
  fun currentVariant(): InlineCompletionPresentableVariant

  @RequiresEdt
  suspend fun useNextVariant(): InlineCompletionPresentableVariant

  @RequiresEdt
  suspend fun usePrevVariant(): InlineCompletionPresentableVariant

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
    ThreadingAssertions.assertEventDispatchThread()
  }

  private val variantsStates = List(variants.size) {
    InlineCompletionVariantState(mutableListOf(), variants[it].data)
  }

  private var currentVariant: InlineCompletionPresentableVariantImpl = run {
    check(variantsStates[0].elements.isEmpty()) // No elements are expected and no elements can be handled (we are outside 'suspend')
    prepareVariant(index = 0, force = true)
  }

  override fun currentVariant(): InlineCompletionPresentableVariant {
    ThreadingAssertions.assertEventDispatchThread()
    return currentVariant
  }

  override suspend fun useNextVariant(): InlineCompletionPresentableVariant {
    ThreadingAssertions.assertEventDispatchThread()
    //disposeCurrentVariant()
    val index = currentVariant.index
    val nextIndex = findStateIndex(index + 1, false) { isPossibleToShow(it) } ?: return currentVariant
    return useVariant(nextIndex, force = false)
  }

  override suspend fun usePrevVariant(): InlineCompletionPresentableVariant {
    ThreadingAssertions.assertEventDispatchThread()
    //disposeCurrentVariant()
    val index = currentVariant.index
    val prevIndex = findStateIndex(index - 1, true) { isPossibleToShow(it) } ?: return currentVariant
    return useVariant(prevIndex, force = false)
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
    //disposeCurrentVariant()
  }

  protected abstract suspend fun elementShown(variantIndex: Int, elementIndex: Int, element: InlineCompletionElement)

  // TODO add direction
  @RequiresEdt
  protected abstract suspend fun beforeVariantSwitched(fromVariantIndex: Int, toVariantIndex: Int, explicit: Boolean)

  @RequiresEdt
  protected abstract suspend fun afterVariantSwitched(fromVariantIndex: Int, toVariantIndex: Int, explicit: Boolean)

  @RequiresEdt
  protected suspend fun elementComputed(variantIndex: Int, elementIndex: Int, element: InlineCompletionElement) {
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

  /**
   * Creates an empty [InlineCompletionPresentableVariant] for the variant [index].
   *
   * [force] means that no checks whether this variant is valid to show are going to be run.
   */
  @RequiresEdt
  private fun prepareVariant(index: Int, force: Boolean): InlineCompletionPresentableVariantImpl {
    ThreadingAssertions.assertEventDispatchThread()
    check(force || isPossibleToShow(index))
    return InlineCompletionPresentableVariantImpl(variantsStates[index].data, index) { element, elementIndex ->
      elementShown(variantIndex = index, elementIndex = elementIndex, element = element)
    }
  }

  /**
   * Creates [InlineCompletionPresentableVariant] for the variant [index] and fills it with elements.
   */
  @RequiresEdt
  private suspend fun getVariant(index: Int, force: Boolean): InlineCompletionPresentableVariantImpl {
    val variant = prepareVariant(index, force)
    val state = variantsStates[index]
    state.elements.forEachIndexed { elementIndex, element ->
      variant.addElement(element, elementIndex)
    }
    return variant
  }

  /**
   * Same as [getVariant], but also sets [currentVariant] to it.
   */
  @RequiresEdt
  private suspend fun useVariant(index: Int, force: Boolean): InlineCompletionPresentableVariantImpl {
    val fromIndex = currentVariant.index
    if (index == fromIndex) {
      return currentVariant
    }

    beforeVariantSwitched(fromIndex, index, !force)
    val variant = getVariant(index, force)
    check(variant.index == index)
    currentVariant = variant
    afterVariantSwitched(fromIndex, index, !force)
    return variant
  }

  protected suspend fun forceNextVariant() {
    val nextIndex = currentVariant.index + 1
    check(nextIndex in variantsStates.indices)
    useVariant(index = nextIndex, force = true)
  }

  private fun isPossibleToShow(index: Int): Boolean {
    validateIndex(index)
    val state = variantsStates[index]
    // TODO not 0
    return (index == currentVariant.index && !state.status.isCompleted()) || state.status.isPossibleToShow()
  }

  private fun validateIndex(index: Int) {
    check(index in variantsStates.indices)
  }
  //
  //private fun disposeCurrentVariant() {
  //  Disposer.dispose(currentVariant)
  //}

  /**
   * Finds the first index that satisfies [predicate]. It starts from [startIndex] and goes over the whole circle.
   * If [reversed] is `true`, then it traverses in reversed order.
   *
   * If no index satisfies [predicate], then `null` is returned.
   */
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
  private val onAdd: suspend (InlineCompletionElement, Int) -> Unit
) : InlineCompletionPresentableVariant {

  @RequiresEdt
  suspend fun addElement(element: InlineCompletionElement, elementIndex: Int) {
    ThreadingAssertions.assertEventDispatchThread()
    onAdd(element, elementIndex)
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
