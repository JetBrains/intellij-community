// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.details.model

import com.intellij.collaboration.async.mapState
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.updateAndGet
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.NonExtendable
interface CodeReviewCommitsChangesStateHandler<C : Any, VM : Any> {
  val selectedCommit: StateFlow<C?>
  val changeListVm: StateFlow<VM>

  fun selectCommit(index: Int): VM?
  fun selectCommit(commit: C?): VM?
  fun selectNextCommit(): VM?
  fun selectPreviousCommit(): VM?

  companion object {
    @JvmOverloads
    fun <C : Any, VM : Any> create(
      cs: CoroutineScope,
      commits: List<C>,
      commitChangesVmProducer: CoroutineScope.(C?) -> VM,
      initialCommitIdx: Int = -1,
    ): CodeReviewCommitsChangesStateHandler<C, VM> =
      CodeReviewCommitsChangesStateHandlerImpl(cs, commits, commitChangesVmProducer, initialCommitIdx)
  }
}

private class CodeReviewCommitsChangesStateHandlerImpl<C : Any, VM : Any>(
  private val cs: CoroutineScope,
  private val commits: List<C>,
  private val commitChangesVmProducer: CoroutineScope.(C?) -> VM,
  initialCommitIdx: Int = -1,
) : CodeReviewCommitsChangesStateHandler<C, VM> {
  private val state = MutableStateFlow(createState(initialCommitIdx))

  override val selectedCommit: StateFlow<C?> = state.mapState { commits.getOrNull(it.commitIdx) }
  override val changeListVm: StateFlow<VM> = state.mapState { it.vm }

  override fun selectCommit(index: Int): VM? {
    if (index > 0 && index !in commits.indices) return null
    return state.updateAndGet {
      it.changeCommit(index)
    }.vm
  }

  override fun selectCommit(commit: C?): VM? {
    val idx = commits.indexOf(commit).takeIf { it >= 0 } ?: return null
    return state.updateAndGet {
      it.changeCommit(idx)
    }.vm
  }

  override fun selectNextCommit(): VM? = state.updateAndGet {
    val newIdx = it.commitIdx + 1
    if (newIdx !in commits.indices) return null // return out of select, do not update
    it.changeCommit(newIdx)
  }.vm

  override fun selectPreviousCommit(): VM? = state.updateAndGet {
    val newIdx = it.commitIdx - 1
    if (newIdx !in commits.indices) return null // return out of select, do not update
    it.changeCommit(newIdx)
  }.vm

  private fun State.changeCommit(commitIdx: Int): State {
    if (this.commitIdx == commitIdx) return this
    vmCs.cancel()
    return createState(commitIdx)
  }

  private fun createState(commitIdx: Int): State {
    val newCs = cs.childScope("Commit Changes View Model")
    val commit = commits.getOrNull(commitIdx)
    return State(commitIdx, newCs, newCs.commitChangesVmProducer(commit))
  }

  private inner class State(
    val commitIdx: Int,
    val vmCs: CoroutineScope,
    val vm: VM
  )
}