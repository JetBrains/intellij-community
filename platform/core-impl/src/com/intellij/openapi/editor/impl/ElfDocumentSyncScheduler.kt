// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.elf.Elf
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.TransferredWriteActionService
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
@ApiStatus.Internal
abstract class ElfDocumentSyncScheduler {
  private var scheduled: Boolean = false

  /**
   * Performs one synchronization pass for the owning document.
   *
   * The scheduler invokes this method on EDT with write access, outside an ELF scope. The invocation starts after
   * the current ELF scope finishes, or immediately when [schedule] is called outside an ELF scope. In both cases,
   * the actual sync runs after the scheduled background write action has been transferred to EDT. Several scheduling
   * requests made before this point are coalesced into this single call.
   */
  protected abstract fun sync()

  /**
   * ELF scope only provides the boundary for starting synchronization; this scheduler owns the per-document scheduling
   * policy. Keep it here so two modified documents get two independent background/transferred write actions instead of
   * one shared write action around all delayed callbacks.
   *
   * The scheduling looks slightly defensive because typing can be interleaved with raw real-document inserts for the same
   * document:
   *
   * 1. Typing enters an ELF scope and appends ELF change `A`. We register one "start document sync after scope" callback
   *    and set [scheduled] so later changes for this document are coalesced into the same scheduled sync.
   * 2. Before that callback reaches EDT with a transferred write action, typing can enter another ELF scope and append
   *    change `B`. Without [scheduled], this would create extra document sync jobs. With it, the already scheduled job
   *    sees both `A` and `B` in the pending ELF changes.
   * 3. A raw real-document insert can arrive before the scheduled job starts, so the selected ELF batch has to be rebased
   *    over real changes.
   *
   * ELF scopes are not expected inside document listeners or other callbacks fired by [sync]. Any changes appended before
   * the sync pass starts are already part of that pass.
   */
  fun schedule() {
    ThreadingAssertions.assertEventDispatchThread()
    if (scheduled) {
      return
    }
    scheduled = true
    val elf = Elf.getElf()
    if (elf.isInElfScope()) {
      elf.performOnScopeFinished(::launchSync)
    }
    else {
      launchSync()
    }
  }

  private fun launchSync() {
    ThreadingAssertions.assertEventDispatchThread()
    invokeLaterWithWriteAccess(::runSync)
  }

  private fun runSync() {
    ThreadingAssertions.assertEventDispatchThread()
    ThreadingAssertions.assertWriteAccess()
    if (Elf.getElf().isInElfScope()) {
      scheduled = false
      schedule()
      return
    }
    try {
      sync()
    } finally {
      scheduled = false
    }
  }

  companion object {
    private val DISPATCHER: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1, "ELF_DOCUMENT_DISPATCHER")

    /**
     * The heart of the "lock-free" typing approach.
     *
     * Lock-free typing is not lock-*less*. The authoritative real document must still be updated under the global write
     * lock, because PSI, undo, persistence and every other read-lock holder rely on that lock for consistency. What
     * changes is *when* and *on which thread* the write lock is taken.
     *
     * In the classic model the keystroke itself takes the write lock on the EDT, so the EDT blocks until all in-flight
     * read actions (indexing, code analysis, ...) drain before the character can be inserted — that wait is the typing
     * freeze. Here, typing only mutates the lock-free elf view on the EDT and returns immediately; updating the real
     * document is deferred to this method and pushed off the critical path:
     *
     * 1. The work is launched on a background coroutine; the single-threaded `DISPATCHER` keeps a document's sync passes
     *    ordered.
     * 2. `backgroundWriteAction` acquires the global write lock *on that background thread*. The expensive part of
     *    taking the write lock — waiting for in-flight read actions to finish — therefore happens off the EDT and never
     *    freezes the UI.
     * 3. `runOnEdtWithTransferredWriteActionAndWait` hands the *already acquired* write lock to the EDT and runs `action`
     *    there. The EDT performs the real-document mutation and fires its listeners with the lock already in hand; it
     *    never blocks acquiring the lock.
     *
     * Net effect: the user sees the typed character instantly (elf view, no lock), while the write-lock-guarded
     * reconciliation into the real document runs asynchronously and coalesced via `schedule`. The EDT is never blocked
     * waiting for the write lock, so typing does not freeze.
     */
    fun invokeLaterWithWriteAccess(@RequiresEdt @RequiresWriteLock action: Runnable) {
      service<MyService>().cs.launch(DISPATCHER) {
        backgroundWriteAction {
          val application = ApplicationManager.getApplication()
          val writeActionService = application.service<TransferredWriteActionService>()
          writeActionService.runOnEdtWithTransferredWriteActionAndWait(action)
        }
      }
    }
  }
}

@Service
private class MyService(val cs: CoroutineScope)
