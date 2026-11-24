// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.ex.DocumentPatch
import com.intellij.openapi.editor.ex.DocumentText
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerDeltaProcessor
import com.intellij.openapi.editor.impl.marker.PMarkerRoot.MarkerEntry
import com.intellij.openapi.util.TextRangeScalarUtil
import com.intellij.util.Processor
import java.util.function.Consumer

/**
 * Buffers disjoint markers in increasing offset and handle order over an empty [base].
 * Each root keeps its own list of markers. Other updates use the direct tree.
 */
internal class PMarkerAppendRoot private constructor(
  private val base: PMarkerDirectRoot,
  private val entries: Entries,
) : PMarkerRoot by base {
  constructor(base: PMarkerDirectRoot) : this(base, Entries())

  override val resolutionCacheIdentity: Any = Any()

  @Volatile
  private var directRoot: PMarkerDirectRoot? = null

  private val size: Int
    get() = entries.size

  override fun isEmpty(): Boolean = size == 0

  override fun isValid(handle: SnapshotMarkerReference<*>): Boolean = find(handle) != null

  override fun resolve(handle: SnapshotMarkerReference<*>): Long {
    val entry = find(handle) ?: return base.resolve(handle)
    return TextRangeScalarUtil.toScalarRange(entry.nodeStart, entry.nodeEnd)
  }

  private fun find(handle: SnapshotMarkerReference<*>): MarkerEntry? {
    if (isEmpty()) return null
    val last = entryAt(size - 1)
    if (handle.id >= last.handle.id) return last.takeIf { it.handle === handle }
    val index = firstIndex { it.handle.id >= handle.id }
    return entryAt(index).takeIf { it.handle === handle }
  }

  private fun entryAt(index: Int): MarkerEntry = entries[index]

  private inline fun firstIndex(matches: (MarkerEntry) -> Boolean): Int {
    var low = 0
    var high = size
    while (low < high) {
      val middle = (low + high) ushr 1
      if (matches(entryAt(middle))) high = middle
      else low = middle + 1
    }
    return low
  }

  override fun processAllRangeMarkers(processor: MarkerDeltaProcessor): Boolean {
    for (index in 0 until size) {
      if (!processor.process(entryAt(index), 0)) return false
    }
    return true
  }

  override fun processRangeMarkersOverlappingWith(
    startOffset: Int,
    endOffset: Int,
    tastePreference: Int,
    processor: Processor<in MarkerEntry>,
  ): Boolean {
    val iterator = overlappingIterator(startOffset, endOffset, tastePreference)
    while (iterator.hasNext()) {
      if (!processor.process(iterator.next())) return false
    }
    return true
  }

  override fun overlappingIterator(startOffset: Int, endOffset: Int, tastePreference: Int): Iterator<MarkerEntry> {
    require(startOffset in 0..endOffset) { "The query range is invalid" }
    if (isEmpty() || entryAt(size - 1).nodeEnd < startOffset) return emptyList<MarkerEntry>().iterator()
    val first = if (size == 1 || entryAt(size - 2).nodeEnd < startOffset) {
      size - 1
    }
    else {
      firstIndex { it.nodeEnd >= startOffset }
    }
    val flags = tastePreference and 0xFF
    return object : AbstractIterator<MarkerEntry>() {
      private var index = first

      override fun computeNext() {
        while (index < size) {
          val entry = entryAt(index++)
          if (entry.nodeStart > endOffset) break
          if (entry.flavorFlags.toInt() and flags == flags) {
            setNext(entry)
            return
          }
        }
        done()
      }
    }
  }

  override fun insert(
    handle: SnapshotMarkerReference<*>,
    startOffset: Int,
    endOffset: Int,
    specMask: Byte,
    flavorFlags: Byte,
    measure: Int,
    policy: MarkerPolicy,
  ): PMarkerRoot {
    val last = if (isEmpty()) null else entryAt(size - 1)
    if (measure != 0 || last != null && (startOffset < last.nodeEnd || handle.id <= last.handle.id)) {
      return materialize().insert(handle, startOffset, endOffset, specMask, flavorFlags, measure, policy)
    }
    require(startOffset >= 0 && endOffset >= startOffset) { "The marker range is invalid" }
    base.checkNewHandle(handle)
    val entry = MarkerEntry(handle, startOffset, endOffset, specMask, flavorFlags, measure, policy)
    return PMarkerAppendRoot(base, entries.add(entry))
  }

  fun materialize(): PMarkerDirectRoot {
    directRoot?.let { return it }
    return synchronized(this) {
      directRoot ?: base.buildFromSortedEntries(List(size, ::entryAt)).also { directRoot = it }
    }
  }

  override fun applyPatch(
    patch: DocumentPatch,
    beforeText: DocumentText,
    afterText: DocumentText,
    invalidatedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
    affectedMarkerConsumer: Consumer<in SnapshotMarkerReference<*>>,
  ): PMarkerRoot = materialize().applyPatch(patch, beforeText, afterText, invalidatedMarkerConsumer, affectedMarkerConsumer)

  override fun restoreEntries(entries: List<MarkerEntry>): PMarkerRoot = update { it.restoreEntries(entries) }

  override fun insertMany(entries: List<MarkerEntry>): PMarkerRoot = update { it.insertMany(entries) }

  override fun updateFlavor(handle: SnapshotMarkerReference<*>, flavorFlags: Byte): PMarkerRoot =
    update { it.updateFlavor(handle, flavorFlags) }

  override fun updateSpec(handle: SnapshotMarkerReference<*>, specMask: Byte): PMarkerRoot = update { it.updateSpec(handle, specMask) }

  override fun updateMeasure(handle: SnapshotMarkerReference<*>, measure: Int): PMarkerRoot = update { it.updateMeasure(handle, measure) }

  override fun remove(handle: SnapshotMarkerReference<*>): PMarkerRoot = update { it.remove(handle) }

  override fun purgeMany(handles: Collection<SnapshotMarkerReference<*>>): PMarkerRoot = update { it.purgeMany(handles) }

  override fun mergeValidMarkersFrom(other: PMarkerRoot): PMarkerRoot = update { it.mergeValidMarkersFrom(other) }

  private inline fun update(action: (PMarkerDirectRoot) -> PMarkerRoot): PMarkerRoot {
    val root = materialize()
    val result = action(root)
    return if (result === root) this else result
  }

  /** Shares full pages between roots and keeps at most [PAGE_SIZE] entries in the tail. */
  private class Entries(
    val size: Int = 0,
    private val pages: PersistentLongMap<Array<MarkerEntry>> = PersistentVector(5),
    private val tail: TailEntry? = null,
  ) {
    operator fun get(index: Int): MarkerEntry {
      val page = index / PAGE_SIZE
      if (page < pages.size()) return pages.getUnchecked(page.toLong())!![index % PAGE_SIZE]
      var current = tail!!
      repeat(size - index - 1) { current = current.previous!! }
      return current.entry
    }

    fun add(entry: MarkerEntry): Entries {
      val newSize = size + 1
      if (newSize % PAGE_SIZE != 0) return Entries(newSize, pages, TailEntry(entry, tail))
      val page = Array(PAGE_SIZE) { entry }
      var current = tail!!
      for (index in PAGE_SIZE - 2 downTo 0) {
        page[index] = current.entry
        if (index != 0) current = current.previous!!
      }
      return Entries(newSize, pages.put(pages.size().toLong(), page))
    }

    private class TailEntry(val entry: MarkerEntry, val previous: TailEntry?)

    private companion object {
      const val PAGE_SIZE = 16
    }
  }
}
