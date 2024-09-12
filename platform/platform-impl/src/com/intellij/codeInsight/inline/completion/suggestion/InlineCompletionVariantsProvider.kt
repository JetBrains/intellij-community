// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.suggestion

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager.UpdateResult
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariantState.Status
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal interface InlineCompletionVariantsProvider : Disposable {

  @RequiresEdt
  fun useNextVariant(): InlineCompletionPresentableVariant

  @RequiresEdt
  fun usePrevVariant(): InlineCompletionPresentableVariant

  @RequiresEdt
  fun captureVariants(): List<InlineCompletionVariant.Snapshot>

  @RequiresEdt
  fun update(event: InlineCompletionEvent, updateManager: (InlineCompletionVariant.Snapshot) -> UpdateResult): Boolean
}

internal abstract class InlineCompletionVariantsComputer @RequiresEdt constructor(
  variants: List<InlineCompletionVariant>,
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

  override fun useNextVariant(): InlineCompletionPresentableVariant {
    ThreadingAssertions.assertEventDispatchThread()
    val index = currentVariant.index
    val nextIndex = findStateIndex(index + 1, false) { isPossibleToShow(it) } ?: return currentVariant
    return useVariant(nextIndex, force = false)
  }

  override fun usePrevVariant(): InlineCompletionPresentableVariant {
    ThreadingAssertions.assertEventDispatchThread()
    val index = currentVariant.index
    val prevIndex = findStateIndex(index - 1, true) { isPossibleToShow(it) } ?: return currentVariant
    return useVariant(prevIndex, force = false)
  }

  override fun captureVariants(): List<InlineCompletionVariant.Snapshot> {
    ThreadingAssertions.assertEventDispatchThread()
    return variantsStates.indices.map { getSnapshot(it) }
  }

  override fun update(event: InlineCompletionEvent, updateManager: (InlineCompletionVariant.Snapshot) -> UpdateResult): Boolean {
    ThreadingAssertions.assertEventDispatchThread()

    var currentVariantResult: UpdateResult? = null

    for ((index, state) in variantsStates.withIndex()) {
      if (state.isInvalidated() || state.status == Status.COMPLETED_EMPTY) {
        continue
      }

      val snapshot = getSnapshot(index)
      val updated = updateManager(snapshot)
      if (currentVariant.index == index) {
        currentVariantResult = updated
      }
      when (updated) {
        is UpdateResult.Changed -> {
          val newSnapshot = updated.snapshot
          if (newSnapshot.elements.isNotEmpty() || index == currentVariant.index) {
            variantChanged(event, index, state.elements, newSnapshot.elements)

            state.elements.clear()
            state.elements.addAll(newSnapshot.elements)
            newSnapshot.data.copyUserDataTo(state.data)
          }
          else {
            invalidate(event, index)
          }
        }
        UpdateResult.Invalidated -> {
          invalidate(event, index)
        }
        UpdateResult.Same -> Unit
      }
    }

    when (currentVariantResult) {
      is UpdateResult.Changed -> updateCurrentVariant()
      UpdateResult.Invalidated -> currentVariantInvalidated()
      UpdateResult.Same, null -> Unit // TODO verify that `null` is okay
    }

    return !variantsStates[currentVariant.index].isInvalidated()
  }

  override fun dispose() = Unit

  @RequiresEdt
  protected abstract fun elementShown(variantIndex: Int, elementIndex: Int, element: InlineCompletionElement)

  @RequiresEdt
  protected abstract fun disposeCurrentVariant()

  @RequiresEdt
  protected abstract fun beforeVariantSwitched(fromVariantIndex: Int, toVariantIndex: Int, explicit: Boolean)

  @RequiresEdt
  protected abstract fun variantChanged(event: InlineCompletionEvent, variantIndex: Int, old: List<InlineCompletionElement>, new: List<InlineCompletionElement>)

  @RequiresEdt
  protected abstract fun variantInvalidated(event: InlineCompletionEvent, variantIndex: Int)

  @RequiresEdt
  protected abstract fun dataChanged()

  @RequiresEdt
  protected fun currentVariant(): InlineCompletionPresentableVariant {
    ThreadingAssertions.assertEventDispatchThread()
    return currentVariant
  }

  @RequiresEdt
  protected fun elementComputed(variantIndex: Int, elementIndex: Int, element: InlineCompletionElement) {
    ThreadingAssertions.assertEventDispatchThread()
    validateIndex(variantIndex)

    val state = variantsStates[variantIndex]
    if (state.isInvalidated()) {
      return
    }

    state.elements += element
    state.status = Status.IN_PROGRESS_NON_EMPTY
    if (currentVariant.index == variantIndex) {
      currentVariant.addElement(element, elementIndex)
    }

    dataChanged() // It makes sense to update every time because data may be shared
  }

  @RequiresEdt
  protected suspend fun variantComputing(variantIndex: Int, computation: suspend () -> Unit): Boolean {
    ThreadingAssertions.assertEventDispatchThread()
    val state = variantsStates[variantIndex]
    coroutineScope {
      state.job = launch {
        ThreadingAssertions.assertEventDispatchThread() // just in case
        if (state.isInvalidated()) {
          return@launch
        }
        variantStartedComputing(variantIndex)
        computation()
        variantComputed(variantIndex)
      }
    }
    return !state.isInvalidated()
  }

  @RequiresEdt
  private fun getSnapshot(index: Int): InlineCompletionVariant.Snapshot {
    val state = variantsStates[index]
    return InlineCompletionVariant.Snapshot(
      state.data,
      state.elements,
      index,
      index == currentVariant.index,
      when (state.status) {
        Status.NOT_STARTED -> InlineCompletionVariant.Snapshot.State.UNTOUCHED
        Status.IN_PROGRESS_EMPTY, Status.IN_PROGRESS_NON_EMPTY -> InlineCompletionVariant.Snapshot.State.IN_PROGRESS
        Status.COMPLETED_EMPTY, Status.COMPLETED_NON_EMPTY -> InlineCompletionVariant.Snapshot.State.COMPUTED
        Status.INVALIDATED -> InlineCompletionVariant.Snapshot.State.INVALIDATED
      }
    )
  }

  @RequiresEdt
  private fun invalidate(event: InlineCompletionEvent, variantIndex: Int) {
    variantInvalidated(event, variantIndex)
    variantsStates[variantIndex].invalidate()
  }

  @RequiresEdt
  private fun variantStartedComputing(variantIndex: Int) {
    ThreadingAssertions.assertEventDispatchThread()

    validateIndex(variantIndex)
    val state = variantsStates[variantIndex]
    if (state.isInvalidated()) {
      return
    }
    state.status = Status.IN_PROGRESS_EMPTY
  }

  @RequiresEdt
  private fun variantComputed(variantIndex: Int) {
    ThreadingAssertions.assertEventDispatchThread()
    validateIndex(variantIndex)

    val state = variantsStates[variantIndex]
    if (state.isInvalidated()) {
      return
    }

    state.status = when (state.status) {
      Status.IN_PROGRESS_EMPTY -> Status.COMPLETED_EMPTY
      Status.IN_PROGRESS_NON_EMPTY -> Status.COMPLETED_NON_EMPTY
      else -> error("Incorrect state of variant status.")
    }

    dataChanged() // It makes sense to update every time because data may be shared
  }

  private fun currentVariantInvalidated() {
    val newIndex = chooseNewVariantAfterInvalidation()
    if (newIndex == null) {
      disposeCurrentVariant() // TODO re-write this part when we drop old InlineCompletionOvertyper
    }
    else {
      useVariant(newIndex, true)
    }
  }

  /**
   * Priority:
   * * Next elements that are non-empty
   * * Previous elements that are non-empty
   * * Next elements that are in progress and might be empty
   */
  private fun chooseNewVariantAfterInvalidation(): Int? {
    val index = currentVariant.index
    for (i in index + 1 until variantsStates.size) {
      if (variantsStates[i].status.isPossibleToShow()) {
        return i
      }
    }
    for (i in index - 1 downTo 0) {
      if (variantsStates[i].status.isPossibleToShow()) {
        return i
      }
    }
    for (i in index + 1 until variantsStates.size) {
      if (variantsStates[i].status.isInProgress()) {
        return i
      }
    }
    return null
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
  private fun getVariant(index: Int, force: Boolean): InlineCompletionPresentableVariantImpl {
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
  private fun useVariant(index: Int, force: Boolean): InlineCompletionPresentableVariantImpl {
    val fromIndex = currentVariant.index
    if (index == fromIndex) {
      return currentVariant
    }

    disposeCurrentVariant()
    beforeVariantSwitched(fromIndex, index, !force)
    val variant = getVariant(index, force)
    check(variant.index == index)
    currentVariant = variant
    dataChanged()
    return variant
  }

  @RequiresEdt
  private fun updateCurrentVariant() {
    disposeCurrentVariant()
    currentVariant = getVariant(currentVariant.index, true)
    dataChanged()
  }

  @RequiresEdt
  protected fun forceNextVariant() {
    val nextIndex = currentVariant.index + 1
    validateIndex(nextIndex)
    useVariant(index = nextIndex, force = true)
  }

  private fun isPossibleToShow(index: Int): Boolean {
    validateIndex(index)
    val state = variantsStates[index]
    return (index == currentVariant.index && !state.status.isCompleted()) || state.status.isPossibleToShow()
  }

  private fun validateIndex(index: Int) {
    check(index in variantsStates.indices)
  }

  /**
   * Finds the first index that satisfies [predicate]. It starts from [startIndex] and goes over the whole circle.
   * If [reversed] is `true`, then it traverses in reversed order.
   *
   * If no index satisfies [predicate], then `null` is returned.
   */
  private inline fun findStateIndex(
    startIndex: Int,
    reversed: Boolean = false,
    predicate: (Int) -> Boolean,
  ): Int? {
    val delta = if (reversed) -1 else 1
    var index = startIndex
    do {
      index = (index + variantsStates.size) % variantsStates.size
      if (predicate(index)) {
        return index
      }
      index += delta
    }
    while (index != startIndex)

    return null
  }
}

internal interface InlineCompletionPresentableVariant {
  val index: Int

  val data: UserDataHolderBase
}

private class InlineCompletionPresentableVariantImpl(
  override val data: UserDataHolderBase,
  override val index: Int,
  private val onAdd: (InlineCompletionElement, Int) -> Unit,
) : InlineCompletionPresentableVariant {

  @RequiresEdt
  fun addElement(element: InlineCompletionElement, elementIndex: Int) {
    ThreadingAssertions.assertEventDispatchThread()
    onAdd(element, elementIndex)
  }
}

private class InlineCompletionVariantState(
  val elements: MutableList<InlineCompletionElement>,
  val data: UserDataHolderBase,
) {

  var status: Status = Status.NOT_STARTED
    set(value) {
      ThreadingAssertions.assertEventDispatchThread()
      check(!isInvalidated())
      field = value
    }

  var job: Job? = null
    set(value) {
      ThreadingAssertions.assertEventDispatchThread()
      check(field == null)
      field = value
    }

  fun invalidate() {
    ThreadingAssertions.assertEventDispatchThread()
    status = Status.INVALIDATED
    elements.clear()
    job?.cancel()
  }

  fun isInvalidated(): Boolean {
    return status == Status.INVALIDATED
  }

  enum class Status {
    NOT_STARTED,
    IN_PROGRESS_EMPTY,
    IN_PROGRESS_NON_EMPTY,
    COMPLETED_EMPTY,
    COMPLETED_NON_EMPTY,
    INVALIDATED
  }
}

private fun Status.isPossibleToShow(): Boolean {
  return this == Status.IN_PROGRESS_NON_EMPTY || this == Status.COMPLETED_NON_EMPTY
}

private fun Status.isCompleted(): Boolean {
  return this == Status.COMPLETED_NON_EMPTY || this == Status.COMPLETED_EMPTY
}

private fun Status.isInProgress(): Boolean {
  return this == Status.IN_PROGRESS_NON_EMPTY || this == Status.IN_PROGRESS_EMPTY
}
