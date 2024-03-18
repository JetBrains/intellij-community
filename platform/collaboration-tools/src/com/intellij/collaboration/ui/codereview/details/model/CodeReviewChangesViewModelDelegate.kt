// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.model

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlin.coroutines.cancellation.CancellationException

class CodeReviewChangesViewModelDelegate<T : CodeReviewChangeListViewModelBase>(
  private val cs: CoroutineScope,
  changesContainer: Flow<Result<CodeReviewChangesContainer>>,
  private val vmProducer: CoroutineScope.(CodeReviewChangesContainer, CodeReviewChangeList) -> T
) {
  constructor(
    cs: CoroutineScope,
    changesContainer: Flow<Result<CodeReviewChangesContainer>>,
    vmProducer: CoroutineScope.(CodeReviewChangeList) -> T
  ) : this(cs, changesContainer, { _, changeList -> vmProducer(changeList) })

  private val selectionRequests = MutableSharedFlow<ChangesRequest>()

  private val _selectedCommit = MutableStateFlow<String?>(null)
  val selectedCommit: StateFlow<String?> = _selectedCommit.asStateFlow()

  val changeListVm: StateFlow<ComputedResult<T>> = changesContainer
    .manageChangeListVm().stateIn(cs, SharingStarted.Lazily, ComputedResult.loading())

  private fun Flow<Result<CodeReviewChangesContainer>>.manageChangeListVm(): Flow<ComputedResult<T>> =
    channelFlow {
      val parentCs = this
      var csAndVm: Pair<CoroutineScope, T>? = null
      collectLatest { changesResult ->
        send(ComputedResult.loading())
        val changes = changesResult.getOrElse {
          if (it is CancellationException) throw it
          send(ComputedResult.failure(it))
          return@collectLatest
        }

        val commits = changes.commits
        val commitsSet = commits.toSet()
        val initialCommit = _selectedCommit.updateAndGet {
          it.takeIf { commitsSet.contains(it) } ?: commitsSet.singleOrNull()
        }

        suspend fun updateChanges(commit: String?, change: RefComparisonChange? = null, force: Boolean = false) {
          val existingCommit = commit.takeIf { commitsSet.contains(it) }
          if (_selectedCommit.value != existingCommit || force) {
            _selectedCommit.value = existingCommit
            csAndVm?.first?.cancelAndJoinSilently()
            val newChangeList = changes.getChangeList(commit)
            val newCs = parentCs.childScope()
            val newVm = vmProducer(changes, newChangeList).apply {
              this.selectChange(change)
            }
            csAndVm = newCs to newVm
            send(ComputedResult.success(newVm))
          }
          else {
            csAndVm?.second?.selectChange(change)
          }
        }

        updateChanges(initialCommit, force = true)

        selectionRequests.collect { request ->
          when (request) {
            is ChangesRequest.Commit -> {
              updateChanges(commits.getOrNull(request.index))
            }
            is ChangesRequest.CommitSha -> {
              updateChanges(request.sha)
            }
            ChangesRequest.NextCommit -> {
              val nextCommit = _selectedCommit.value?.let(commits::indexOf)?.let {
                commits.getOrNull(it + 1)
              } ?: return@collect
              updateChanges(nextCommit)
            }
            ChangesRequest.PrevCommit -> {
              val prevCommit = _selectedCommit.value?.let(commits::indexOf)?.let {
                commits.getOrNull(it - 1)
              } ?: return@collect
              updateChanges(prevCommit)
            }
            is ChangesRequest.SelectChange -> {
              updateChanges(changes.commitsByChange[request.change], request.change)
            }
          }
        }
      }
    }

  fun selectCommit(index: Int) {
    cs.launchNow {
      selectionRequests.emit(ChangesRequest.Commit(index))
    }
  }

  fun selectCommit(commitSha: String) {
    cs.launchNow {
      selectionRequests.emit(ChangesRequest.CommitSha(commitSha))
    }
  }

  fun selectNextCommit() {
    cs.launchNow {
      selectionRequests.emit(ChangesRequest.NextCommit)
    }
  }

  fun selectPreviousCommit() {
    cs.launchNow {
      selectionRequests.emit(ChangesRequest.PrevCommit)
    }
  }

  fun selectChange(change: RefComparisonChange) {
    cs.launchNow {
      selectionRequests.emit(ChangesRequest.SelectChange(change))
    }
  }

  private fun CodeReviewChangesContainer.getChangeList(commit: String?): CodeReviewChangeList =
    if (commit == null) {
      CodeReviewChangeList(null, summaryChanges)
    }
    else {
      CodeReviewChangeList(commit, changesByCommits[commit].orEmpty())
    }
}

private sealed interface ChangesRequest {
  data class Commit(val index: Int) : ChangesRequest
  data class CommitSha(val sha: String) : ChangesRequest
  data object NextCommit : ChangesRequest
  data object PrevCommit : ChangesRequest
  data class SelectChange(val change: RefComparisonChange) : ChangesRequest
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