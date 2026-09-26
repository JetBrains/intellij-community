// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers

import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.LanguageUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.impl.FreeThreadedFileViewProvider
import com.intellij.psi.impl.PsiDocumentManagerEx
import com.intellij.util.containers.ContainerUtil
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Tracks a PSI element inside a language injection. Stores a host-context smart pointer and the
 * injected range in host-file coordinates, converting between host and injected coordinate spaces
 * on restoration. Handles non-editable affix fragments (prefix/suffix around the injected code).
 */
internal class InjectedSelfElementInfo(
  project: Project,
  injectedElement: PsiElement,
  injectedRange: TextRange,
  containingFile: PsiFile,
  private val myHostContext: SmartPsiElementPointer<PsiLanguageInjectionHost>,
) : SmartPointerElementInfo {

  private val myInjectedFileRangeInHostFile: SmartPsiFileRange
  private val myAffixOffsets: AffixOffsets?
  private val myType: Identikit

  init {
    assert(containingFile.viewProvider is FreeThreadedFileViewProvider) { "element parameter must be an injected element: $injectedElement; $containingFile" }
    assert(injectedRange in containingFile.getTextRange()) { "Injected range outside the file: " + injectedRange + "; file: " + containingFile.getTextRange() }

    val ilm = InjectedLanguageManager.getInstance(project)
    val hostRange = ilm.injectedToHost(injectedElement, injectedRange)
    val hostFile = myHostContext.getContainingFile()!!
    assert(hostFile.viewProvider !is FreeThreadedFileViewProvider) { "hostContext parameter must not be and injected element: $myHostContext" }
    val smartPointerManager = SmartPointerManager.getInstance(project)
    myInjectedFileRangeInHostFile = smartPointerManager.createSmartPsiFileRangePointer(hostFile, hostRange)
    myType = Identikit.fromPsi(injectedElement, LanguageUtil.getRootLanguage(containingFile))

    var startAffixIndex = -1
    var startAffixOffset = -1
    var endAffixIndex = -1
    var endAffixOffset = -1
    val fragments = ilm.getNonEditableFragments(containingFile.getViewProvider().getDocument() as DocumentWindow)
    for (i in fragments.indices) {
      val range = fragments[i]
      if (range.containsOffset(injectedRange.startOffset)) {
        startAffixIndex = i
        startAffixOffset = injectedRange.startOffset - range.startOffset
      }
      if (range.containsOffset(injectedRange.endOffset)) {
        endAffixIndex = i
        endAffixOffset = injectedRange.endOffset - range.startOffset
      }
    }
    myAffixOffsets = if (startAffixIndex >= 0 || endAffixIndex >= 0) AffixOffsets(startAffixIndex, startAffixOffset, endAffixIndex, endAffixOffset) else null
  }

  override val virtualFile: VirtualFile?
    get() {
      val element = restoreElement(SmartPointerManagerEx.getInstanceEx(project)) ?: return null
      return element.containingFile.virtualFile
    }

  override fun getRange(manager: SmartPointerManagerEx): Segment? = getInjectedRange(false)

  override fun getPsiRange(manager: SmartPointerManagerEx): Segment? = getInjectedRange(true)

  override fun restoreElement(manager: SmartPointerManagerEx): PsiElement? {
    val hostFile = myHostContext.getContainingFile()?.takeIf { it.isValid } ?: return null
    val hostContext = myHostContext.getElement() ?: return null
    val segment = myInjectedFileRangeInHostFile.getPsiRange() ?: return null

    val injectedPsi = getInjectedFileIn(hostContext, hostFile, TextRange.create(segment))
    val rangeInInjected = hostToInjected(true, segment, injectedPsi, myAffixOffsets) ?: return null

    return myType.findPsiElement(injectedPsi, rangeInInjected.startOffset, rangeInInjected.endOffset)
  }

  private fun getInjectedFileIn(
    hostContext: PsiElement,
    hostFile: PsiFile,
    rangeInHostFile: TextRange,
  ): PsiFile? {
    val docManager = PsiDocumentManager.getInstance(project) as PsiDocumentManagerEx

    var result: PsiFile? = null

    fun processInjectedFile(injectedPsi: PsiFile) {
      val document = docManager.getDocument(injectedPsi) as? DocumentWindow ?: return
      val window = docManager.getLastCommittedDocument(document) as DocumentWindow
      val hostRange = window.injectedToHost(TextRange(0, injectedPsi.textLength))
      if (rangeInHostFile in hostRange) {
        result = injectedPsi
      }
    }

    val document = docManager.getDocument(hostFile)
    val injectionManager = InjectedLanguageManager.getInstance(project)
    if (document != null && docManager.isUncommited(document)) {
      val documents = injectionManager.getCachedInjectedDocumentsInRange(hostFile, rangeInHostFile)
      val injectedFiles = ContainerUtil.map2SetNotNull(documents) { d -> docManager.getPsiFile(d) }
      for (injectedFile in injectedFiles) {
        processInjectedFile(injectedFile)
      }
    }
    else {
      val injected = injectionManager.getInjectedPsiFiles(hostContext)
      if (injected != null) {
        val injectedFiles = ContainerUtil.map2SetNotNull(injected) { pair -> pair.first.containingFile }
        for (injectedFile in injectedFiles) {
          processInjectedFile(injectedFile)
        }
      }
    }

    return result
  }

  override fun pointsToTheSameElementAs(other: SmartPointerElementInfo, manager: SmartPointerManagerEx): Boolean {
    if (javaClass != other.javaClass) return false
    if ((other as InjectedSelfElementInfo).myHostContext != myHostContext) return false
    val myElementInfo = (myInjectedFileRangeInHostFile as SmartPsiElementPointerImpl<*>).elementInfo
    val oElementInfo = (other.myInjectedFileRangeInHostFile as SmartPsiElementPointerImpl<*>).elementInfo
    return myElementInfo.pointsToTheSameElementAs(oElementInfo, manager)
  }

  override fun restoreFile(manager: SmartPointerManagerEx): PsiFile? {
    val hostFile = myHostContext.getContainingFile()?.takeIf { it.isValid } ?: return null
    val hostContext = myHostContext.getElement() ?: return null
    val segment = myInjectedFileRangeInHostFile.getPsiRange() ?: return null

    val rangeInHostFile = TextRange.create(segment)
    return getInjectedFileIn(hostContext, hostFile, rangeInHostFile)
  }

  private fun getInjectedRange(psi: Boolean): ProperTextRange? {
    if (myHostContext.element == null) return null

    val hostElementRange = (if (psi) myInjectedFileRangeInHostFile.psiRange else myInjectedFileRangeInHostFile.range) ?: return null

    val injectedFile = restoreFile(SmartPointerManagerEx.getInstanceEx(project))
    return hostToInjected(psi, hostElementRange, injectedFile, myAffixOffsets)
  }

  override fun cleanup() {
    SmartPointerManager.getInstance(project).removePointer(myInjectedFileRangeInHostFile)
  }

  override val documentToSynchronize: Document?
    get() = (myHostContext as SmartPsiElementPointerImpl<*>).elementInfo.documentToSynchronize

  override fun elementHashCode(): Int {
    return (myHostContext as SmartPsiElementPointerImpl<*>).elementInfo.elementHashCode()
  }

  private val project: Project
    get() = myHostContext.project

  override fun toString(): String = "injected{type=$myType, range=$myInjectedFileRangeInHostFile, host=$myHostContext}"
}

@OptIn(ExperimentalContracts::class)
private fun hostToInjected(
  psi: Boolean,
  hostRange: Segment,
  injectedFile: PsiFile?,
  affixOffsets: AffixOffsets?,
): ProperTextRange? {
  contract {
    returnsNotNull() implies (injectedFile != null)
  }

  val virtualFile = injectedFile?.virtualFile as? VirtualFileWindow ?: return null
  val project = injectedFile.project
  var documentWindow = virtualFile.getDocumentWindow()
  if (psi) {
    documentWindow = (PsiDocumentManager.getInstance(project) as PsiDocumentManagerEx).getLastCommittedDocument(documentWindow) as DocumentWindow
  }

  val start = documentWindow.hostToInjected(hostRange.getStartOffset())
  val end = documentWindow.hostToInjected(hostRange.getEndOffset())

  if (affixOffsets != null) {
    return affixOffsets.expandRangeToAffixes(start, end, InjectedLanguageManager.getInstance(project).getNonEditableFragments(documentWindow))
  }
  return ProperTextRange.create(start, end)
}

private class AffixOffsets(
  val startAffixIndex: Int,
  val startAffixOffset: Int,
  val endAffixIndex: Int,
  val endAffixOffset: Int,
) {
  init {
    assert(startAffixIndex < 0 ||
           endAffixIndex < 0 ||
           startAffixOffset >= 0 && endAffixOffset >= 0 && (startAffixIndex < endAffixIndex || startAffixIndex == endAffixIndex && startAffixOffset <= endAffixOffset)
    ) {
      "Invalid offsets passed: startAffixIndex = $startAffixIndex;endAffixIndex = $endAffixIndex;startAffixOffset = $startAffixOffset;endAffixOffset = $endAffixOffset"
    }
  }

  fun expandRangeToAffixes(start: Int, end: Int, fragments: List<TextRange>): ProperTextRange? {
    return ProperTextRange.create(
      shiftOffsetOrDefault(fragments, startAffixIndex, startAffixOffset, start) ?: return null,
      shiftOffsetOrDefault(fragments, endAffixIndex, endAffixOffset, end) ?: return null
    )
  }
}

private fun shiftOffsetOrDefault(fragments: List<TextRange>, fragmentIndex: Int, offset: Int, defaultOffset: Int): Int? {
  if (fragmentIndex < 0) {
    return defaultOffset
  }

  if (fragmentIndex >= fragments.size) {
    return null
  }

  val fragment = fragments[fragmentIndex]
  if (fragment.length < offset) {
    return null
  }

  TextRange.assertProperRange(fragment)
  return fragment.startOffset + offset
}
