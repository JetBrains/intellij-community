// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.model

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.map
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface CodeReviewChangesViewModelDelegate<T> {
  val selectedCommit: StateFlow<String?>
  val changeListVm: StateFlow<ComputedResult<T>>

  fun selectCommit(index: Int): T?
  fun selectCommit(commitSha: String?): T?
  fun selectNextCommit(): T?
  fun selectPreviousCommit(): T?

  companion object {
    fun <T> create(
      cs: CoroutineScope,
      changesContainer: Flow<Result<CodeReviewChangesContainer>>,
      vmProducer: CoroutineScope.(CodeReviewChangesContainer, CodeReviewChangeList) -> T,
    ): CodeReviewChangesViewModelDelegate<T> {
      return CodeReviewChangesViewModelDelegateImpl(cs, changesContainer, vmProducer)
    }
  }
}

private class CodeReviewChangesViewModelDelegateImpl<T>(
  private val cs: CoroutineScope,
  changesContainer: Flow<Result<CodeReviewChangesContainer>>,
  private val vmProducer: CoroutineScope.(CodeReviewChangesContainer, CodeReviewChangeList) -> T,
) : CodeReviewChangesViewModelDelegate<T> {
  private val state = MutableStateFlow<Result<State<T>>?>(null)

  override val selectedCommit: StateFlow<String?> = state.mapState { it?.getOrNull()?.commit }
  override val changeListVm: StateFlow<ComputedResult<T>> = state.mapState { ComputedResult(it).map(State<T>::vm) }

  init {
    cs.launch {
      changesContainer.collect { result ->
        state.update {
          it?.getOrNull()?.vmCs?.cancel()
          result.map { changes ->
            createState(changes)
          }
        }
      }
    }
  }

  override fun selectCommit(index: Int): T? = updateState {
    val commit = it.changes.commits.getOrNull(index)
    it.changeCommit(commit)
  }?.vm

  override fun selectCommit(commitSha: String?): T? = updateState {
    it.changeCommit(commitSha)
  }?.vm

  override fun selectNextCommit(): T? = updateState { state ->
    val commits = state.changes.commits
    val nextCommit = state.commit?.let(commits::indexOf)?.let {
      commits.getOrNull(it + 1)
    } ?: return@updateState state
    state.changeCommit(nextCommit)
  }?.vm

  override fun selectPreviousCommit(): T? = updateState { state ->
    val commits = state.changes.commits
    val nextCommit = state.commit?.let(commits::indexOf)?.let {
      commits.getOrNull(it - 1)
    } ?: return@updateState state
    state.changeCommit(nextCommit)
  }?.vm

  private fun CodeReviewChangesContainer.getChangeList(commit: String?): CodeReviewChangeList =
    if (commit == null) {
      CodeReviewChangeList(null, summaryChanges)
    }
    else {
      CodeReviewChangeList(commit, changesByCommits[commit].orEmpty())
    }

  private fun updateState(function: (State<T>) -> State<T>): State<T>? =
    state.updateAndGet {
      it?.map(function)
    }?.getOrNull()

  private fun State<T>.changeCommit(newCommit: String?): State<T> {
    if (newCommit == commit) return this
    vmCs.cancel()
    return createState(changes, newCommit)
  }

  private fun createState(changes: CodeReviewChangesContainer, commit: String? = null): State<T> {
    val newCs = cs.childScope("Change List View Model")
    return State(changes, commit, newCs, newCs.vmProducer(changes, changes.getChangeList(commit)))
  }

  private class State<T>(
    val changes: CodeReviewChangesContainer,
    val commit: String?,
    val vmCs: CoroutineScope,
    val vm: T,
  )
}

open class CodeReviewChangesContainer(val summaryChanges: List<RefComparisonChange>,
                                      val commits: List<String>,
                                      val changesByCommits: Map<String, List<RefComparisonChange>>) {
  val commitsByChange: Map<RefComparisonChange, String> = mutableMapOf<RefComparisonChange, String>().apply {
    changesByCommits.entries.forEach { (commit, changes) ->
      changes.forEach {
        put(it, commit)
      }
    }
  }
}