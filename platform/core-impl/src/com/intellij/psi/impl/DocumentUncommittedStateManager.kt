// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.injected.editor.DocumentWindow
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
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
import com.intellij.psi.util.PsiVersioningService
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.annotations.TestOnly
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicInteger
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

  private val timelineLock = Any()

  private val isolatedTimelines: ConcurrentMap<Document, IsolatedTimeline>
    get() {
      require(Thread.holdsLock(timelineLock))
      return _isolatedTimelines
    }

  private val _isolatedTimelines: ConcurrentMap<Document, IsolatedTimeline> = CollectionFactory.createConcurrentWeakMap()

  /**
   * A tracker of live timelines, that is needed for garbage collection after document ceases to be strongly reachable.
   */
  private val liveTimelines: MutableSet<IsolatedTimeline>
    get() {
      require(Thread.holdsLock(timelineLock))
      return _liveTimelines
    }

  private val _liveTimelines: MutableSet<IsolatedTimeline> = ConcurrentHashMap.newKeySet()

  companion object {
    private val LOG = Logger.getInstance(DocumentUncommittedStateManager::class.java)
  }

  /**
   * A shared object that represents an accessor to isolated document commits.
   *
   * The purpose is the following:
   * ```kotlin
   * allowIsolatedCommits { commitDocument() }
   * allowIsolatedCommits { document.getPsiFile() }
   * ```
   * The computation in the second `allowIsolatedCommits` sees the result computed in the first one
   */
  private class IsolatedTimeline(val version: PsiVersioningService.OpaquePsiVersion, val document: WeakReference<Document>) {
    // initially, the holders equal to one because they are shared between different blocks.
    private val holders = AtomicInteger(1)

    fun acquire() {
      val newValue = holders.incrementAndGet()
      if (newValue >= 3) {
        // at this point, it is not expected that a single timeline is used by two clients concurrently
        try {
          LOG.error("Too much parallelism")
        } catch (_: Throwable) {
          // do nothing -- protection against broken IDE in tests
        }
      }
    }

    /**
     * Called when a client no longer needs timeline
     */
    fun release() {
      if (holders.decrementAndGet() == 0) {
        PsiVersioningService.forgetForkedTimeline(version)
      }
    }

    fun getNumberOfHolders(): Int {
      return holders.get()
    }
  }

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
    terminateIsolatedTimeline(document)
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
    synchronized(timelineLock) {
      // just erasing all timelines
      removeTimelines { true }
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
    // cleaning up timelines that correspond to strongly unreachable references
    removeTimelines { it.document.get() == null }
  }

  /**
   * Runs [action] on the isolated timeline of [document].
   * See [com.intellij.psi.PsiDocumentManager.allowIsolatedCommits] for the contract.
   *
   * The timeline outlives the block when the block committed [document], so the next call reuses it.
   * A block that committed nothing retires the timeline, because a later call has nothing to reuse.
   * A frozen forked version holds the cleanup barrier of the whole application, so an unused fork must not stay.
   */
  fun <T> allowIsolatedCommits(document: Document, action: Supplier<out T>): T {
    val application = ApplicationManager.getApplication()
    val actualDocument = if (document is DocumentWindow) document.delegate else document
    if (application.isWriteIntentLockAcquired) {
      // The caller holds a lock, so a commit inside goes to the main timeline.
      // A fork here would install a version that conflicts with the version of the lock.
      return action.get()
    }
    if (application.isReadAccessAllowed) {
      throw IllegalStateException("Isolated commits are not allowed under read lock")
    }
    if (isInForkedTimeline()) {
      assertIsolatedTimelineOf(actualDocument)
      return action.get()
    }
    if (InternalPsiVersioning.isInsideVersioningButNotLocks()) {
      // so we are in `freezePsiVersion`
      LOG.error("Commits are not permitted in `freezePsiVersion`")
      return action.get()
    }
    val timeline = initPreacquiredTimeline(actualDocument)
    try {
      return PsiVersioningService.executeWithTimeline(timeline.version) {
        try {
          action.get()
        } finally {
          // we need to check for side effects here because in this block we still have a forked timeline
          handleTimelineWithoutSideEffects(actualDocument, timeline)
        }
      }
    } finally {
      // this `release` is paired with pre-acquisition in `initTimeline`
      // Normally, the number of holders in `timeline` is now `1` -- the timeline is going to be reused between versions.
      timeline.release()
    }
  }

  /**
   * Imagine the following scenario: someone decided to run action with allowed isolated commits, but no commits happened.
   * In this case we do not need to maintain the isolated timeline, as it holds nothing.
   */
  private fun handleTimelineWithoutSideEffects(document: Document, timeline: IsolatedTimeline) {
    if (doesForkedBaselineExist(document)) {
      return
    }
    synchronized(timelineLock) {
      if (doesForkedBaselineExist(document)) {
        return@handleTimelineWithoutSideEffects
      }
      val removalSuccessful = isolatedTimelines.remove(document, timeline)
      if (!removalSuccessful) {
        // can happen with concurrent publish
        return
      }
      liveTimelines.remove(timeline)
      timeline.release()
    }
  }

  private fun doesForkedBaselineExist(document: Document): Boolean {
    val state = peek(document) ?: return false
    return state.forked.containsKey(InternalPsiVersioning.getCurrentPsiVersion())
  }

  /**
   * Reports an error when the current forked timeline does not belong to [document].
   *
   * A nested call keeps the timeline of the outer call, so the two documents would share it.
   */
  private fun assertIsolatedTimelineOf(document: Document) = synchronized(timelineLock) {
    val installed = PsiVersioningService.currentOpaqueVersion() ?: return@synchronized
    val captured = isolatedTimelines[document]?.version
    if (installed == captured) {
      return@synchronized
    }
    if (captured == null) {
      LOG.error("The document '$document' has no isolated timeline, but the current computation runs in '$installed'")
    }
    else {
      LOG.error("Timeline mismatch: the current computation runs in '$installed', but the document '$document' uses '$captured'")
    }
  }

  /**
   * Returns timeline with number of holders of at least `2`
   */
  private fun initPreacquiredTimeline(document: Document): IsolatedTimeline = synchronized(timelineLock) {
    val existing = isolatedTimelines[document]
    if (existing != null) {
      require(existing.getNumberOfHolders() > 0) {
        "Registered timelines must have at least one holder"
      }
      existing.acquire()
      return@synchronized existing
    }
    val created = IsolatedTimeline(PsiVersioningService.forkTimeline(), WeakReference(document))
    // Publish the record before the map entry, so a concurrent publish can always find it and release it.
    liveTimelines.add(created)
    val installed = isolatedTimelines.putIfAbsent(document, created)
    if (installed != null) {
      error("Breach of mutual exclusion: the document '$document' already has an associated timeline")
    }
    InternalPsiVersioning.recordVersionedChange(this)
    created.acquire()
    return created
  }

  /**
   * Releases the isolated timeline of [document], because a publish ended it.
   *
   * A publish retires the record that the map holds now, whichever record that is.
   */
  private fun terminateIsolatedTimeline(document: Document) = synchronized(timelineLock) {
    val removedTimeline = isolatedTimelines.remove(document)
    if (removedTimeline == null) {
      // no one used isolated commits for this document
      return@synchronized
    }
    val removalFromLiveTimelines = liveTimelines.remove(removedTimeline)
    check(removalFromLiveTimelines) { "Timeline not found" }
    removedTimeline.release()
  }

  /**
   * Releases the isolated timeline of each document that satisfies [predicate]
   */
  private fun removeTimelines(predicate: (IsolatedTimeline) -> Boolean) = synchronized(timelineLock) {
    val snapshot = liveTimelines.toList()
    val timelinesToBeRemoved = mutableSetOf<IsolatedTimeline>()
    for (timeline in snapshot) {
      if (predicate(timeline)) {
        timelinesToBeRemoved.add(timeline)
      }
    }
    liveTimelines.removeAll(timelinesToBeRemoved)
    timelinesToBeRemoved.forEach { it.release() }
    isolatedTimelines.values.removeIf { it in timelinesToBeRemoved }
  }
}
