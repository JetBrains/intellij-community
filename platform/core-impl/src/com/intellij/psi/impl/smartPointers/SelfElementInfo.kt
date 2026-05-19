// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers

import com.intellij.codeInsight.multiverse.withAllowedIrrelevantContexts
import com.intellij.lang.Language
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.FrozenDocument
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UnfairTextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiDocumentManagerEx
import org.jetbrains.annotations.ApiStatus
import kotlin.concurrent.Volatile

/**
 * Tracks a regular PSI element inside a file via an [Identikit] (element type + optional anchor)
 * and text offsets. Offsets are kept in sync with document edits through [SmartPointerTracker].
 * The main workhorse of the hierarchy.
 */
@ApiStatus.Internal
open class SelfElementInfo internal constructor(
  range: ProperTextRange?,
  identikit: Identikit,
  containingPsiFile: PsiFile,
  val isForInjected: Boolean,
  manager: SmartPointerManagerEx?,
) : SmartPointerElementInfo, ContextAwareInfo {
  @Volatile
  private var myIdentikit: Identikit = identikit

  @Volatile
  override var fileHolder: FileHolder = FileHolder.createInterned(containingPsiFile, manager)

  override val virtualFile: VirtualFile
    get() = fileHolder.virtualFile

  var psiStartOffset: Int = 0
    private set

  var psiEndOffset: Int = 0
    private set

  init {
    setRange(range)
  }

  fun switchToAnchor(element: PsiElement) {
    switchTo(element, findAnchor(element))
  }

  private fun findAnchor(element: PsiElement): Pair<Identikit.ByAnchor, PsiElement>? {
    val language = myIdentikit.fileLanguage ?: return null
    return Identikit.withAnchor(element, language)
  }

  private fun switchTo(element: PsiElement, pair: Pair<Identikit.ByAnchor, PsiElement>?) {
    if (pair == null) {
      setRange(element.textRange)
      return
    }

    assert(pair.first.hashCode() == myIdentikit.hashCode())
    myIdentikit = pair.first
    setRange(pair.second.textRange)
  }

  fun updateRangeToPsi(pointerRange: Segment, cachedElement: PsiElement): Boolean {
    val pair = findAnchor(cachedElement)
    val range = (pair?.second ?: cachedElement).textRange
    if (range == null || !range.intersects(pointerRange)) {
      return false
    }
    switchTo(cachedElement, pair)
    return true
  }

  fun setRange(range: Segment?) {
    if (range == null) {
      psiStartOffset = -1
      psiEndOffset = -1
    }
    else {
      psiStartOffset = range.startOffset
      psiEndOffset = range.endOffset
    }
  }

  fun hasRange(): Boolean = this.psiStartOffset >= 0

  val isGreedy: Boolean
    get() = isForInjected || myIdentikit.isForPsiFile()

  override val documentToSynchronize: Document?
    get() = ourFileDocManager.getCachedDocument(virtualFile)

  override fun restoreElement(manager: SmartPointerManagerEx): PsiElement? {
    val segment = getPsiRange(manager) ?: return null
    val file = restoreFile(manager)?.takeIf { it.isValid } ?: return null
    return myIdentikit.findPsiElement(file, segment.startOffset, segment.endOffset)
  }

  override fun getPsiRange(manager: SmartPointerManagerEx): TextRange? = calcPsiRange()

  private fun calcPsiRange(): TextRange? =
    if (hasRange()) UnfairTextRange(psiStartOffset, psiEndOffset) else null

  override fun restoreFile(manager: SmartPointerManagerEx): PsiFile? {
    val language = myIdentikit.fileLanguage ?: return null
    val holder = fileHolder
    val vfile = restoreVFile(holder.virtualFile)

    val tracker = SmartPointerManagerEx.getInstanceEx(manager.project).getTracker(holder.virtualFile)

    if (vfile == null) {
      fileHolder = FileHolder.createInterned(holder.virtualFile, null, tracker)
      return null
    }
    tracker?.revalidate(vfile, manager)
    return restoreFileFromVirtual({ fileHolder }, manager.project, language, manager.getTracker(vfile))
  }

  override fun cleanup() {
    setRange(null)
  }

  override fun elementHashCode(): Int = virtualFile.hashCode() + myIdentikit.hashCode() * 31

  override fun pointsToTheSameElementAs(other: SmartPointerElementInfo, manager: SmartPointerManagerEx): Boolean {
    if (other !is SelfElementInfo) {
      return false
    }
    if (virtualFile != other.virtualFile || myIdentikit !== other.myIdentikit) return false

    return runReadActionBlocking {
      val range1 = getPsiRange(manager)
      val range2 = other.getPsiRange(manager)
      range1 != null && range2 != null && range1.startOffset == range2.startOffset && range1.endOffset == range2.endOffset
    }
  }

  override fun getRange(manager: SmartPointerManagerEx): Segment? {
    if (hasRange()) {
      val document = documentToSynchronize
      if (document != null) {
        val documentManager = manager.psiDocumentManager
        val events = documentManager.getEventsSinceCommit(document)
        if (!events.isEmpty()) {
          val tracker = manager.getTracker(virtualFile)
          if (tracker != null) {
            return tracker.getUpdatedRange(this, documentManager.getLastCommittedDocument(document) as FrozenDocument, events)
          }
        }
      }
    }
    return calcPsiRange()
  }

  override fun toString(): String {
    return "psi:range=" + calcPsiRange() + ",type=" + myIdentikit
  }

  companion object {
    @Suppress("ApplicationServiceAsStaticFinalFieldOrProperty")
    private val ourFileDocManager = FileDocumentManager.getInstance()

    @JvmStatic
    internal fun restoreFileFromVirtual(
      fileHolder: () -> FileHolder,
      project: Project,
      language: Language,
      tracker: SmartPointerTracker?,
    ): PsiFile? {
      return runReadActionBlocking {
        if (project.isDisposed()) return@runReadActionBlocking null

        val vfile = restoreVFile(fileHolder().virtualFile) ?: return@runReadActionBlocking null

        // updates file-holders for all pointers of the file
        val smartPointerManager = SmartPointerManagerEx.getInstanceEx(project)
        tracker?.revalidate(vfile, smartPointerManager)

        val context = fileHolder().context ?: return@runReadActionBlocking null
        val file = withAllowedIrrelevantContexts {
          PsiManager.getInstance(project).findFile(vfile, context)
        } ?: return@runReadActionBlocking null
        val effectiveLanguage = if (language === Language.ANY) file.viewProvider.baseLanguage else language
        return@runReadActionBlocking file.viewProvider.getPsi(effectiveLanguage)
      }
    }

    @JvmStatic
    internal fun restoreDirectoryFromVirtual(virtualFile: VirtualFile, project: Project): PsiDirectory? {
      return runReadActionBlocking {
        if (project.isDisposed()) return@runReadActionBlocking null
        val child = restoreVFile(virtualFile)?.takeIf { it.isValid } ?: return@runReadActionBlocking null
        val file = PsiManager.getInstance(project).findDirectory(child)?.takeIf { it.isValid }
        file
      }
    }

    internal fun restoreVFile(virtualFile: VirtualFile): VirtualFile? {
      if (virtualFile.isValid()) {
        return virtualFile
      }

      val vParent = virtualFile.parent?.takeIf { it.isValid } ?: return null
      return vParent.findChild(virtualFile.name)
    }

    @JvmStatic
    fun calcActualRangeAfterDocumentEvents(
      containingFile: PsiFile,
      document: Document,
      segment: Segment,
      isSegmentGreedy: Boolean,
    ): Segment? {
      val project = containingFile.project
      val documentManager = PsiDocumentManager.getInstance(project) as PsiDocumentManagerEx
      val events = documentManager.getEventsSinceCommit(document).ifEmpty { return null }

      val pointerManager = SmartPointerManagerEx.getInstanceEx(project)
      val tracker = pointerManager.getTracker(containingFile.viewProvider.virtualFile) ?: return null

      return tracker.getUpdatedRange(
        containingFile = containingFile,
        segment = segment,
        isSegmentGreedy = isSegmentGreedy,
        frozen = documentManager.getLastCommittedDocument(document) as FrozenDocument,
        events = events
      )
    }
  }
}
