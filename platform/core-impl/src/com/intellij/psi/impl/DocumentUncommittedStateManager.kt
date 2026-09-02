// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.injected.editor.DocumentWindow
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.elf.Elf
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.FrozenDocument
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning
import com.intellij.psi.impl.source.tree.mvcc.InternalPsiVersioning.isInForkedTimeline
import com.intellij.psi.impl.source.tree.mvcc.PsiVersionCleanable
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.annotations.TestOnly
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Supplier

/**
 * Keeps the uncommitted state of every document of one project, outside versioned storage.
 *
 * The version counter advances with each write action, but a version advance does not always indicate a document commit.
 * Data bound to a forked version stays relevant when the write action counter is far ahead.
 * This class tracks its data ([Baseline]) for forked timelines and for the main version at the same time.
 */
internal class DocumentUncommittedStateManager : PsiVersionCleanable {

  /**
   * This user data maps each [Document] to its [DocumentState] without an external strong reference to the state.
   * A document state strongly references its document, so its lifetime must match the document lifetime.
   */
  private val stateKey = Key.create<DocumentState>("UNCOMMITTED_DOCUMENT_STATE")

  private val liveStates: MutableMap<DocumentState, Boolean> = CollectionFactory.createConcurrentWeakMap()

  /**
   * The text that a PSI tree matches, together with the document events that came after it.
   * Each lightweight commit advances the baseline for its forked version.
   */
  internal class Baseline private constructor(
    val frozen: FrozenDocument,
    private val events: EventLog,
    private val watermark: Int,
  ) {
    private val frozenWindows: ConcurrentMap<DocumentWindow, DocumentWindow> = ConcurrentHashMap()

    fun appendEvent(event: DocumentEvent) {
      events.append(event)
    }

    fun eventCount(): Int = events.size

    fun fork(frozen: FrozenDocument, watermark: Int): Baseline = Baseline(frozen, events, watermark)

    fun getFrozenWindow(window: DocumentWindow, freezer: Supplier<out DocumentWindow>): DocumentWindow {
      return frozenWindows[window] ?: ConcurrencyUtil.cacheOrGet(frozenWindows, window, freezer.get())
    }

    fun getEventsSince(): List<DocumentEvent> = events.sliceFrom(watermark)

    fun hasEventsSince(): Boolean = events.size > watermark

    companion object {
      fun create(frozen: FrozenDocument): Baseline = Baseline(frozen, EventLog(), 0)
    }
  }

  /**
   * An append-only log of document events.
   *
   * A document change appends an event. A forked timeline reads a slice while it holds no lock.
   * A plain `ArrayList` cannot do this because it gives no happens-before relation for its elements.
   * It can also reallocate during a read.
   * This log publishes the size last with a volatile write.
   * A reader that observes a size also observes each element below it.
   * Growth is amortized, unlike a copy-on-write list, which copies on each keystroke.
   */
  private class EventLog {
    @Volatile
    private var data = arrayOfNulls<DocumentEvent>(4)

    @Volatile
    var size: Int = 0
      private set

    /**
     * A write action serializes an append for a document on the write thread.
     * Some documents permit a change without a lock, so this monitor gives the log one writer.
     */
    @Synchronized
    fun append(event: DocumentEvent) {
      var currentData = data
      val currentSize = size
      if (currentSize == currentData.size) {
        currentData = currentData.copyOf(currentSize * 2)
        // Publish the larger array before the size can point into it.
        data = currentData
      }
      currentData[currentSize] = event
      // The volatile size write makes the append visible to a reader.
      size = currentSize + 1
    }

    fun sliceFrom(from: Int): List<DocumentEvent> {
      // Read the size first. The larger array is published before the size can exceed the old array length.
      val currentSize = size
      val currentData = data
      if (from >= currentSize) {
        return emptyList()
      }
      val slice = Array(currentSize - from) { currentData[from + it]!! }
      return Collections.unmodifiableList(slice.asList())
    }
  }

  private class DocumentState {
    // visible in the main timeline
    @Volatile
    var main: Baseline? = null

    // visible in the forked timelines
    val forked: ConcurrentMap<Long, Baseline> = ConcurrentHashMap()
  }

  // Returns the state if it exists.
  private fun peek(document: Document): DocumentState? = document.getUserData(stateKey)

  /**
   * Returns the state and creates it if it does not exist.
   */
  private fun state(document: DocumentImpl): DocumentState {
    document.getUserData(stateKey)?.let { return it }
    val created = DocumentState()
    val installed = (document as UserDataHolderEx).putUserDataIfAbsent(stateKey, created)
    if (installed === created) {
      liveStates[created] = true
    }
    return installed
  }

  /**
   * Returns the baseline for the current computation timeline.
   * Returns `null` when the document has no pending change.
   *
   * A fork that did not commit this document reads the main baseline.
   * Its last committed text is the published text, and each pending event applies to it.
   */
  private fun baseline(document: Document): Baseline? {
    val state = peek(document) ?: return null
    val version = InternalPsiVersioning.getCurrentPsiVersion()
    if (isInForkedTimeline()) {
      state.forked[version]?.let { return it }
    }
    return state.main
  }

  /**
   * Starts the main baseline of [document] when it has none.
   * Call this before the change, so the frozen text is the text before the change.
   */
  fun startBaselineIfAbsent(document: DocumentImpl) {
    val state = state(document)
    if (state.main == null) {
      state.main = Baseline.create(document.freeze())
    }
  }

  /**
   * Appends one document event to the main baseline.
   */
  fun appendEvent(event: DocumentEvent) {
    val document = event.document
    val isElfScope = Elf.getElf().isInElfScope()
    val isWriteThreadOnlyDocument = (document is DocumentImpl) && document.isWriteThreadOnly
    if (!isElfScope && isWriteThreadOnlyDocument) {
      ThreadingAssertions.assertWriteAccess()
    }
    peek(document)?.main?.appendEvent(event)
  }

  /** The text that the PSI of the current timeline matches, or `null` when the document has no pending change. */
  fun getLastCommittedText(document: Document): FrozenDocument? = baseline(document)?.frozen

  fun getEventsSinceCommit(document: Document): List<DocumentEvent> = baseline(document)?.getEventsSince() ?: emptyList()

  /** Returns whether the document of the current timeline waits for a commit. */
  fun hasPendingEvents(document: Document): Boolean = baseline(document)?.hasEventsSince() == true

  /**
   * Returns the frozen injected window of [window] from the cache of the current timeline.
   * A forked timeline gets its own cache.
   * A window frozen from forked host ranges cannot reach the main timeline.
   */
  fun getFrozenWindow(
    hostDocument: Document,
    window: DocumentWindow,
    freezer: Supplier<out DocumentWindow>,
  ): DocumentWindow {
    val baseline = baseline(hostDocument) ?: return freezer.get()
    return baseline.getFrozenWindow(window, freezer)
  }

  /** Returns the event count that a forked commit stores as its watermark. */
  fun currentEventCount(document: Document): Int = peek(document)?.main?.eventCount() ?: 0

  /**
   * Records the result of a lightweight commit for the forked timeline of the current computation.
   * This method is an analogue of [publishCommit] for forked timelines.
   *
   * Pass the frozen text and the watermark that the commit captured when it read the new document text.
   * A fork holds no lock, so a later value can describe text that the forked PSI did not match.
   * When the document changes before this call, the new event lands at the watermark.
   * The stored baseline then reports a pending change, and the fork commits again.
   */
  fun recordForkedCommit(document: Document, frozen: FrozenDocument, watermark: Int) {
    val state = peek(document)
    val main = state?.main ?: return
    val version = InternalPsiVersioning.getCurrentPsiVersion()
    state.forked[version] = main.fork(frozen, watermark)
    InternalPsiVersioning.recordVersionedChange(this)
  }

  /**
   * Ends the main baseline because the document is published, and drops each forked baseline.
   *
   * Each forked baseline holds a watermark into the event log that this call retires.
   * After a publish, a forked text has no defined relation to the published text.
   *
   * @return the ended baseline, or `null` when the document had no pending change
   */
  fun publishCommit(document: Document): Baseline? {
    if (document is DocumentImpl && document.isWriteThreadOnly) {
      ThreadingAssertions.assertWriteAccess()
    }
    val state = peek(document) ?: return null
    val main = state.main
    state.main = null
    state.forked.clear()
    return main
  }

  @TestOnly
  fun forgetEverything() {
    for (state in liveStates.keys) {
      state.main = null
      state.forked.clear()
    }
  }

  /**
   * Drops the baseline of each forked timeline that no live version can enter again.
   * A live fork at `V + 1` holds the barrier at `V`.
   * `minVersionForCleaning` rounds a frozen version down to an even value, so its baseline survives.
   * This method does not drop a main baseline. Only a publish ends it.
   */
  override fun liveVersionChanged(minVersion: Long) {
    for (state in liveStates.keys) {
      state.forked.keys.removeIf { version -> version < minVersion }
    }
  }
}
