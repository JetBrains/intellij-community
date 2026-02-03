package com.intellij.notebooks.visualization

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.MessageBus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * This class frees stored data and pointers at some time after pointer invalidation. Implementation is thread-safe.
 * Once pointer becomes invalid its data removed at least once.
 * Don't use this class if you want preserve data when pointer could become valid again via 'undo' action.
 *
 * Implementation assumes that NotebookIntervalPointer removed in two consecutive steps:
 * 1. pointer becomes invalid (.get() == null)
 * 2. this class receives event from messageBus
 */
class NotebookIntervalPointerConcurrentMap<Value>(messageBus: MessageBus, parent: Disposable) : CheckedDisposable {

  private val mapReference = AtomicReference(ConcurrentHashMap<NotebookIntervalPointer, Value>())

  private val factoryListener = object : NotebookIntervalPointerFactory.ChangeListener {
    override fun onUpdated(event: NotebookIntervalPointersEvent) {
      for (change in event.changes) {
        when (change) {
          is NotebookIntervalPointersEvent.OnRemoved -> removePointers(change)
          is NotebookIntervalPointersEvent.OnEdited -> Unit
          is NotebookIntervalPointersEvent.OnInserted -> Unit
          is NotebookIntervalPointersEvent.OnSwapped -> Unit
        }
      }
    }
  }

  init {
    messageBus.connect(parent).subscribe(NotebookIntervalPointerFactory.ChangeListener.TOPIC, factoryListener)
    Disposer.register(parent, this)
  }

  fun getAllIntervals(): Set<NotebookIntervalPointer> {
    return mapReference.get()?.keys ?: emptySet()
  }

  fun getAll(): List<Pair<NotebookIntervalPointer, Value>> {
    return mapReference.get()?.toList() ?: emptyList()
  }

  private fun removePointers(removed: NotebookIntervalPointersEvent.OnRemoved) {
    val map = mapReference.get() ?: return
    for (p in removed.subsequentPointers) {
      map.remove(p.pointer)
    }
  }

  operator fun get(cellPointer: NotebookIntervalPointer): Value? {
    return mapReference.get()?.get(cellPointer)
  }

  operator fun get(cellOrdinal: Int): Value? {
    val intervalPointer = getAllIntervals().find { it.get()?.ordinal == cellOrdinal } ?: return null
    return get(intervalPointer)
  }

  operator fun set(cellPointer: NotebookIntervalPointer, value: Value) {
    if (cellPointer.get() == null) return
    mapReference.get()?.set(cellPointer, value)

    if (cellPointer.get() == null) {
      // Probably pointer was removed concurrently and written again
      // need to remove it because it is invalid
      mapReference.get()?.remove(cellPointer)
    }
  }

  fun remove(cellPointer: NotebookIntervalPointer): Value? {
    return mapReference.get()?.remove(cellPointer)
  }

  fun clear() {
    mapReference.get()?.keys?.clear()
  }


  /**
   * It's possible to pass any collection, but implementation has special mode for a case with large Set<NotebookIntervalPointer>
   * see [java.util.concurrent.ConcurrentHashMap.CollectionView.removeAll]
   */
  fun removeAll(cellPointers: Collection<NotebookIntervalPointer>): Boolean {
    return mapReference.get()?.keys?.removeAll(cellPointers) == true
  }

  override fun dispose() {
    mapReference.set(null)
  }

  override fun isDisposed(): Boolean {
    return mapReference.get() == null
  }
}