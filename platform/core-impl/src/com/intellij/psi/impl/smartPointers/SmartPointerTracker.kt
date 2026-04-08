// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.impl.FrozenDocument
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.Segment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.Arrays

/**
 * Tracks smart pointers for a single file.
 *
 * ** GC **
 *
 * Instances of [SmartPsiElementPointerImpl] are stored on weak references.
 * Corresponding [SelfElementInfo] instances are stored in [MarkerCache] on hard references.
 * Once [SmartPsiElementPointerImpl] is garbage-collected, the corresponding [SelfElementInfo] is removed from [MarkerCache].
 */
@ApiStatus.Internal
class SmartPointerTracker {
  private var nextAvailableIndex = 0
  private var size: Int = 0
  private var references = arrayOfNulls<PointerReference>(10)
  private val markerCache = MarkerCache(this)
  private var mySorted = false

  @JvmName("addReference")
  @Synchronized
  internal fun addReference(pointer: SmartPsiElementPointerImpl<*>) {
    val elementInfo = pointer.elementInfo
    require(elementInfo is SelfElementInfo)
    val reference = PointerReference(pointer, this)
    if (needsExpansion() || isTooSparse()) {
      resize()
    }

    if (references[nextAvailableIndex] != null) {
      throw AssertionError(references[nextAvailableIndex])
    }

    storePointerReference(references, nextAvailableIndex++, reference)
    size++
    mySorted = false
    if (pointer.selfInfo.hasRange()) {
      markerCache.rangeChanged()
    }
  }

  private fun needsExpansion(): Boolean =
    nextAvailableIndex >= references.size

  private fun isTooSparse(): Boolean =
    nextAvailableIndex > size * 2

  private fun resize() {
    val newReferences = arrayOfNulls<PointerReference>(size * 3 / 2 + 1)
    var index = 0
    // don't use processAlivePointers/removeReference since it can unregister the whole pointer list, and we're not prepared to that
    for (ref in references) {
      if (ref != null) {
        storePointerReference(newReferences, index++, ref)
      }
    }
    assert(index == size) { "$index != $size" }
    references = newReferences
    nextAvailableIndex = index
  }

  @JvmName("removeReference")
  @Synchronized
  internal fun removeReference(reference: PointerReference) {
    val index = reference.index
    if (index < 0) return

    if (reference != references[index]) {
      throw AssertionError("At " + index + " expected " + reference + ", found " + references[index])
    }
    reference.index = -1
    references[index] = null
    size--
  }

  private fun processAlivePointers(processor: Processor<in SmartPsiElementPointerImpl<*>>) {
    for (i in 0..<nextAvailableIndex) {
      val ref = references[i] ?: continue

      val pointer = ref.get() ?: run {
        removeReference(ref)
        continue
      }

      if (!processor.process(pointer)) {
        return
      }
    }
  }

  private fun ensureSorted() {
    if (mySorted) return

    val pointers = ArrayList<SmartPsiElementPointerImpl<*>>()
    processAlivePointers(CommonProcessors.CollectProcessor(pointers))
    if (size != pointers.size) throw AssertionError()

    pointers.sortWith { p1, p2 ->
      MarkerCache.INFO_COMPARATOR.compare(p1.selfInfo, p2.selfInfo)
    }

    for (i in pointers.indices) {
      storePointerReference(references, i, pointers[i].pointerReference!!)
    }
    Arrays.fill(references, pointers.size, nextAvailableIndex, null)
    nextAvailableIndex = pointers.size
    mySorted = true
  }

  @JvmName("updateMarkers")
  @Synchronized
  internal fun updateMarkers(frozen: FrozenDocument, events: List<DocumentEvent>) {
    val stillSorted = markerCache.updateMarkers(frozen, events)
    if (!stillSorted) {
      mySorted = false
    }
  }

  @JvmName("getUpdatedRange")
  @Synchronized
  internal fun getUpdatedRange(
    info: SelfElementInfo,
    document: FrozenDocument,
    events: List<DocumentEvent>,
  ): Segment? {
    return markerCache.getUpdatedRange(info, document, events)
  }

  @JvmName("getUpdatedRange")
  @Synchronized
  internal fun getUpdatedRange(
    containingFile: PsiFile,
    segment: Segment,
    isSegmentGreedy: Boolean,
    frozen: FrozenDocument,
    events: List<DocumentEvent>,
  ): Segment? {
    return MarkerCache.getUpdatedRange(containingFile, segment, isSegmentGreedy, frozen, events)
  }

  @JvmName("switchStubToAst")
  @Synchronized
  internal fun switchStubToAst(info: AnchorElementInfo, element: PsiElement) {
    info.switchToTreeRange(element)
    markerCache.rangeChanged()
    mySorted = false
  }

  @JvmName("fastenBelts")
  @Synchronized
  internal fun fastenBelts(manager: SmartPointerManagerEx) {
    processQueue()
    processAlivePointers { pointer ->
      pointer.selfInfo.fastenBelt(manager)
      true
    }
  }

  @JvmName("updatePointerTargetsAfterReparse")
  @Synchronized
  internal fun updatePointerTargetsAfterReparse() {
    processAlivePointers { pointer ->
      if (pointer !is SmartPsiFileRangePointerImpl) {
        updatePointerTarget(pointer, pointer.psiRange)
      }
      true
    }
  }

  private fun <E : PsiElement> updatePointerTarget(
    pointer: SmartPsiElementPointerImpl<E>,
    pointerRange: Segment?,
  ) {
    val cachedElement = pointer.getCachedElement() ?: return

    val cachedValid = cachedElement.isValid()
    if (cachedValid) {
      if (pointerRange == null) {
        // document change could be damaging, but if PSI survived after reparse, let's point to it
        pointer.selfInfo.switchToAnchor(cachedElement)
        return
      }

      // after reparse and its complex tree diff, the element might have "moved" to other range
      // but if an element of the same type can still be found at the old range, let's point there
      if (pointerRange == cachedElement.getTextRange()) {
        return
      }
    }

    val actual = pointer.doRestoreElement()
    if (actual == null && cachedValid && pointer.selfInfo.updateRangeToPsi(pointerRange!!, cachedElement)) {
      return
    }

    if (actual !== cachedElement) {
      pointer.cacheElement(actual)
    }
  }

  private fun storePointerReference(references: Array<PointerReference?>, index: Int, ref: PointerReference) {
    references[index] = ref
    ref.index = index
  }

  @JvmName("getSortedInfos")
  @Synchronized
  internal fun getSortedInfos(): List<SelfElementInfo> {
    ensureSorted()

    val infos = ArrayList<SelfElementInfo>(size)
    processAlivePointers { pointer ->
      val info = pointer.selfInfo
      if (!info.hasRange()) return@processAlivePointers false

      infos.add(info)
      true
    }
    return infos
  }

  @TestOnly
  @Synchronized
  fun getSize(): Int = size

  private val SmartPsiElementPointerImpl<*>.selfInfo: SelfElementInfo
    get() = this.elementInfo as SelfElementInfo

  internal class PointerReference(
    pointer: SmartPsiElementPointerImpl<*>,
    val tracker: SmartPointerTracker
  ) : WeakReference<SmartPsiElementPointerImpl<*>?>(pointer, ourQueue) {
    var index = -2

    init {
      pointer.pointerReference = this
    }
  }

  companion object {
    private val ourQueue = ReferenceQueue<SmartPsiElementPointerImpl<*>?>()

    init {
      val application = ApplicationManager.getApplication()
      if (!application.isDisposed()) {
        LowMemoryWatcher.register({ processQueue() }, application)
      }
    }

    @JvmStatic
    @VisibleForTesting
    fun processQueue() {
      while (true) {
        val reference = ourQueue.poll() as PointerReference? ?: break

        check(reference.get() == null) { "Queued reference has referent!" }

        reference.tracker.removeReference(reference)
      }
    }
  }
}
