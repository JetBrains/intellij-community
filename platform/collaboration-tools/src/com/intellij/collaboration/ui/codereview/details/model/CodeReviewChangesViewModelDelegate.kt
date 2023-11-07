// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.model

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.util.CODE_REVIEW_CHANGE_HASHING_STRATEGY
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlin.coroutines.cancellation.CancellationException

class CodeReviewChangesViewModelDelegate<T : MutableCodeReviewChangeListViewModel>(
  private val cs: CoroutineScope,
  changesContainer: Flow<Result<CodeReviewChangesContainer>>,
  private val vmProducer: CoroutineScope.() -> T
) {
  private val selectionRequests = MutableSharedFlow<ChangesRequest>()

  private val _selectedCommit = MutableStateFlow<String?>(null)
  val selectedCommit: StateFlow<String?> = _selectedCommit.asStateFlow()

  val changeListVm: SharedFlow<Result<T>> = changesContainer
    .manageChangeListVm().shareIn(cs, SharingStarted.Lazily, 1)

  private fun Flow<Result<CodeReviewChangesContainer>>.manageChangeListVm(): Flow<Result<T>> =
    channelFlow {
      val vm: T = vmProducer()
      collectLatest { changesResult ->
        val changes = changesResult.getOrElse {
          if (it is CancellationException) throw it
          send(Result.failure(it))
          return@collectLatest
        }

        val commits = changes.commits
        val commitsSet = commits.toSet()
        val initialCommit = _selectedCommit.updateAndGet {
          it.takeIf { commitsSet.contains(it) } ?: commitsSet.firstOrNull()
        }

        fun updateCommit(commit: String?, change: Change? = null) {
          val existingCommit = commit.takeIf { commitsSet.contains(it) }
          _selectedCommit.value = existingCommit
          vm.updatesChanges(changes, existingCommit, change)
        }

        updateCommit(initialCommit)
        send(Result.success(vm))

        selectionRequests.collect { request ->
          when (request) {
            is ChangesRequest.Commit -> {
              updateCommit(commits.getOrNull(request.index))
            }
            is ChangesRequest.CommitSha -> {
              updateCommit(request.sha)
            }
            ChangesRequest.NextCommit -> {
              val nextCommit = _selectedCommit.value?.let(commits::indexOf)?.let {
                commits.getOrNull(it + 1)
              } ?: return@collect
              updateCommit(nextCommit)
            }
            ChangesRequest.PrevCommit -> {
              val prevCommit = _selectedCommit.value?.let(commits::indexOf)?.let {
                commits.getOrNull(it - 1)
              } ?: return@collect
              updateCommit(prevCommit)
            }
            is ChangesRequest.SelectChange -> {
              updateCommit(changes.commitsByChange[request.change], request.change)
            }
            is ChangesRequest.SelectCommitAndFile -> {
              val changeSet = changes.getChanges(request.commitSha)
              val change = changeSet.find {
                it.afterRevision?.file == request.filePath || it.beforeRevision?.file == request.filePath
              }
              updateCommit(request.commitSha, change)
            }
            is ChangesRequest.SelectFile -> {
              val commit = _selectedCommit.value
              val changeSet = changes.getChanges(commit)
              val change = changeSet.find {
                it.afterRevision?.file == request.filePath || it.beforeRevision?.file == request.filePath
              }
              updateCommit(commit, change)
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

  fun selectChange(change: Change) {
    cs.launchNow {
      selectionRequests.emit(ChangesRequest.SelectChange(change))
    }
  }

  fun selectChange(commitSha: String?, filePath: FilePath) {
    cs.launchNow {
      selectionRequests.emit(ChangesRequest.SelectCommitAndFile(commitSha, filePath))
    }
  }

  fun selectFile(filePath: FilePath) {
    cs.launchNow {
      selectionRequests.emit(ChangesRequest.SelectFile(filePath))
    }
  }
}

private sealed interface ChangesRequest {
  data class Commit(val index: Int) : ChangesRequest
  data class CommitSha(val sha: String) : ChangesRequest
  object NextCommit : ChangesRequest
  object PrevCommit : ChangesRequest
  data class SelectChange(val change: Change) : ChangesRequest
  data class SelectCommitAndFile(val commitSha: String?, val filePath: FilePath) : ChangesRequest
  data class SelectFile(val filePath: FilePath) : ChangesRequest
}

class CodeReviewChangesContainer(val summaryChanges: List<Change>,
                                 val commits: List<String>,
                                 val changesByCommits: Map<String, List<Change>>) {
  val commitsByChange: Map<Change, String> = CollectionFactory
    .createCustomHashingStrategyMap<Change, String>(changesByCommits.entries.fold(0) { acc, (_, changes) -> acc + changes.size },
                                                    CODE_REVIEW_CHANGE_HASHING_STRATEGY).apply {
      changesByCommits.entries.forEach { (commit, changes) ->
        changes.forEach {
          put(it, commit)
        }
      }
    }
}

fun CodeReviewChangesContainer.getChanges(commit: String?): List<Change> =
  if (commit == null) summaryChanges else changesByCommits[commit].orEmpty()