// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.impl.FrozenDocument
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.Segment
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.CommonProcessors
import com.intellij.util.Processor
import com.intellij.util.containers.CollectionFactory
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
class SmartPointerTracker(initialModCount: Long) {
  @Volatile
  private var validationModCount: Long = initialModCount
  private val selfInfoList = WeakPointerReferenceList()
  private val fileInfoList = WeakPointerReferenceList()
  private val markerCache = MarkerCache(this)
  private var mySorted = false
  private val holderCache = CollectionFactory.createConcurrentWeakKeySoftValueMap<CodeInsightContext, FileHolder>()
  private val pendingContextMappings = mutableListOf<Map<CodeInsightContext, CodeInsightContext?>>()

  @JvmName("startTracking")
  @Synchronized
  internal fun startTracking(pointer: SmartPsiElementPointerImpl<*>) {
    val elementInfo = pointer.elementInfo as ContextAwareInfo

    when (elementInfo) {
      is SelfElementInfo -> {
        val reference = SelfElementPointerReference(pointer, this)
        selfInfoList.add(reference)
        mySorted = false
        if (pointer.selfInfo.hasRange()) {
          markerCache.rangeChanged()
        }
      }
      is FileElementInfo -> {
        val reference = FilePointerReference(pointer, this)
        fileInfoList.add(reference)
      }
      else -> {
        throw IllegalArgumentException("Unexpected element info: $elementInfo")
      }
    }
  }

  @Synchronized
  private fun removeReference(reference: PointerReference) {
    when (reference) {
      is SelfElementPointerReference -> selfInfoList.remove(reference)
      is FilePointerReference -> fileInfoList.remove(reference)
    }
  }

  private fun ensureSorted() {
    if (mySorted) return
    selfInfoList.sort { p1, p2 ->
      MarkerCache.INFO_COMPARATOR.compare(p1.selfInfo, p2.selfInfo)
    }
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
    selfInfoList.processAlivePointers { pointer ->
      pointer.selfInfo.fastenBelt(manager)
      true
    }
  }

  @JvmName("updatePointerTargetsAfterReparse")
  @Synchronized
  internal fun updatePointerTargetsAfterReparse() {
    selfInfoList.processAlivePointers { pointer ->
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

  @JvmName("getSortedInfos")
  @Synchronized
  internal fun getSortedInfos(): List<SelfElementInfo> {
    ensureSorted()

    val infos = ArrayList<SelfElementInfo>(selfInfoList.size)
    selfInfoList.processAlivePointers { pointer ->
      val info = pointer.selfInfo
      if (!info.hasRange()) return@processAlivePointers false

      infos.add(info)
      true
    }
    return infos
  }

  @Synchronized
  internal fun getFileInfos(): List<FileElementInfo> = buildList(fileInfoList.size) {
    fileInfoList.processAlivePointers { pointer ->
      val info = pointer.elementInfo as FileElementInfo
      this@buildList.add(info)
      true
    }
  }

  @TestOnly
  @Synchronized
  fun getSize(): Int = selfInfoList.size

  fun isPossiblyInvalidated(manager: SmartPointerManagerEx): Boolean =
    manager.possiblyInvalidationModCounter.modificationCount > validationModCount

  @Synchronized
  internal fun pushContextMapping(mapping: Map<CodeInsightContext, CodeInsightContext?>) {
    pendingContextMappings.add(mapping)
  }

  @JvmName("clearPendingState")
  @Synchronized
  internal fun clearPendingState() {
    pendingContextMappings.clear()
    holderCache.clear()
  }

  private val SmartPsiElementPointerImpl<*>.selfInfo: SelfElementInfo
    get() = this.elementInfo as SelfElementInfo

  @Synchronized
  fun revalidate(virtualFile: VirtualFile, manager: SmartPointerManagerEx) {
    val currentModCount = manager.possiblyInvalidationModCounter.modificationCount
    if (validationModCount >= currentModCount) return

    validationModCount = currentModCount

    val allInfos = getSortedInfos() + getFileInfos()
    if (allInfos.isEmpty()) {
      pendingContextMappings.clear()
      return
    }

    if (pendingContextMappings.isNotEmpty()) {
      val composedMapping = composeMappings(pendingContextMappings)
      pendingContextMappings.clear()

      for (info in allInfos) {
        val ctx = info.fileHolder.context ?: continue
        if (ctx in composedMapping) {
          info.fileHolder = createFileHolderInterned(virtualFile, composedMapping[ctx])
        }
      }

      for (oldCtx in composedMapping.keys) {
        holderCache.remove(oldCtx)
      }
      return
    }

    // Fallback: no stored mappings available
    val oldContexts = allInfos.mapNotNullTo(mutableSetOf()) { it.fileHolder.context }
    val actualContexts = CodeInsightContextManager.getInstance(manager.project).getCodeInsightContexts(virtualFile).toSet()
    val deadContexts = oldContexts.subtract(actualContexts)

    if (deadContexts.isEmpty()) {
      return
    }

    if (deadContexts.size == 1 && actualContexts.size == 1 && oldContexts.size == 1) {
      val actualContext = actualContexts.single()
      val deadContext = deadContexts.single()
      val newHolder = createFileHolderInterned(virtualFile, actualContext)
      for (info in allInfos) {
        info.fileHolder = newHolder
      }
      holderCache.remove(deadContext)
      holderCache[actualContext] = newHolder
      return
    }

    for (info in allInfos) {
      if (info.fileHolder.context in deadContexts) {
        info.fileHolder = createFileHolderInterned(virtualFile, null)
      }
    }
    for (deadContext in deadContexts) {
      holderCache.remove(deadContext)
    }
  }

  internal fun createFileHolderInterned(virtualFile: VirtualFile, context: CodeInsightContext?): FileHolder =
    holderCache.computeIfAbsent(context ?: NullContext) { c -> FileHolder.create(c.takeUnless { it === NullContext }, virtualFile) }

  private object NullContext: CodeInsightContext

  private fun composeMappings(
    mappings: List<Map<CodeInsightContext, CodeInsightContext?>>,
  ): Map<CodeInsightContext, CodeInsightContext?> {
    val composed = mutableMapOf<CodeInsightContext, CodeInsightContext?>()
    for (mapping in mappings) {
      for (key in composed.keys) {
        val value = composed[key]
        if (value != null && value in mapping) {
          composed[key] = mapping[value]
        }
      }
      for ((key, value) in mapping) {
        if (key !in composed) {
          composed[key] = value
        }
      }
    }
    return composed
  }

  /**
   * A weak reference to a `SmartPsiElementPointerImpl`.
   * Is used for storing smart pointers in [WeakPointerReferenceList].
   * Gets automatically removed from the list once the referent is garbage-collected.
   *
   * There are two subclasses:
   * - [SelfElementPointerReference] for self-pointers
   * - [FilePointerReference] for file-pointers
   *
   * We need them to be able to remove the reference from the corresponding list ([selfInfoList] or [fileInfoList]) when the referent is garbage-collected.
   */
  internal sealed class PointerReference(
    pointer: SmartPsiElementPointerImpl<*>,
    private val tracker: SmartPointerTracker
  ) : WeakReference<SmartPsiElementPointerImpl<*>?>(pointer, ourQueue) {
    var index = -2

    init {
      pointer.pointerReference = this
    }

    fun isPossiblyInvalidated(manager: SmartPointerManagerEx): Boolean =
      tracker.isPossiblyInvalidated(manager)

    fun delete() {
      tracker.removeReference(this)
    }
  }

  private class FilePointerReference(
    pointer: SmartPsiElementPointerImpl<*>,
    tracker: SmartPointerTracker
  ) : PointerReference(pointer, tracker)

  private class SelfElementPointerReference(
    pointer: SmartPsiElementPointerImpl<*>,
    tracker: SmartPointerTracker
  ) : PointerReference(pointer, tracker)

  /**
   * Manages a list of weak references to `SmartPsiElementPointerImpl` objects.
   *
   * Works in collaboration with [PointerReference].
   *
   * Not synchronized.
   */
  private class WeakPointerReferenceList {
    private var references = arrayOfNulls<PointerReference>(10)
    private var nextAvailableIndex = 0

    var size: Int = 0
      private set

    fun add(reference: PointerReference) {
      if (needsExpansion() || isTooSparse()) {
        resize()
      }

      if (references[nextAvailableIndex] != null) {
        throw AssertionError(references[nextAvailableIndex])
      }

      storePointerReference(references, nextAvailableIndex++, reference)
      size++
    }

    fun remove(reference: PointerReference) {
      val index = reference.index
      if (index < 0) return

      if (reference != references[index]) {
        throw AssertionError("At $index expected $reference, found ${references[index]}")
      }
      reference.index = -1
      references[index] = null
      size--
    }

    fun processAlivePointers(processor: Processor<in SmartPsiElementPointerImpl<*>>) {
      for (i in 0..<nextAvailableIndex) {
        val ref = references[i] ?: continue

        val pointer = ref.get() ?: run {
          remove(ref)
          continue
        }

        if (!processor.process(pointer)) {
          return
        }
      }
    }

    fun sort(comparator: Comparator<SmartPsiElementPointerImpl<*>>) {
      val pointers = ArrayList<SmartPsiElementPointerImpl<*>>()
      processAlivePointers(CommonProcessors.CollectProcessor(pointers))
      if (size != pointers.size) throw AssertionError()

      pointers.sortWith(comparator)

      for (i in pointers.indices) {
        storePointerReference(references, i, pointers[i].pointerReference!!)
      }
      Arrays.fill(references, pointers.size, nextAvailableIndex, null)
      nextAvailableIndex = pointers.size
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

    private fun storePointerReference(references: Array<PointerReference?>, index: Int, ref: PointerReference) {
      references[index] = ref
      ref.index = index
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

        reference.delete()
      }
    }
  }
}
