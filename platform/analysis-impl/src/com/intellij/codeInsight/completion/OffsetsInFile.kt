// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.ChangedPsiRangeUtil
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.text.BlockSupport

/**
 * @author peter
 */
class OffsetsInFile(val file: PsiFile, val offsets: OffsetMap) {
  constructor(file: PsiFile) : this(file, OffsetMap(file.viewProvider.document!!))

  fun toTopLevelFile(): OffsetsInFile {
    val manager = InjectedLanguageManager.getInstance(file.project)
    val hostFile = manager.getTopLevelFile(file)
    if (hostFile == file) return this
    return OffsetsInFile(hostFile, offsets.mapOffsets(hostFile.viewProvider.document!!) { manager.injectedToHost(file, it) })
  }

  fun toInjectedIfAny(offset: Int): OffsetsInFile {
    val manager = InjectedLanguageManager.getInstance(file.project)
    val injected = manager.findInjectedElementAt(file, offset)?.containingFile ?: return this
    val virtualFile = injected.virtualFile
    if (virtualFile is VirtualFileWindow) {
      val documentWindow = virtualFile.documentWindow
      return OffsetsInFile(injected, offsets.mapOffsets(
        documentWindow) { documentWindow.hostToInjected(it) })
    }
    else {
      return this
    }
  }

  fun copyWithReplacement(startOffset: Int, endOffset: Int, replacement: String): OffsetsInFile {
    return replaceInCopy(file.copy() as PsiFile, startOffset, endOffset, replacement)
  }

  private fun replaceInCopy(fileCopy: PsiFile, startOffset: Int, endOffset: Int, replacement: String): OffsetsInFile {
    val tempDocument = DocumentImpl(offsets.document.immutableCharSequence, true)
    val tempMap = offsets.copyOffsets(tempDocument)
    tempDocument.replaceString(startOffset, endOffset, replacement)

    reparseFile(fileCopy, tempDocument.immutableCharSequence)

    val copyOffsets = tempMap.copyOffsets(fileCopy.viewProvider.document!!)
    return OffsetsInFile(fileCopy, copyOffsets)
  }

  private fun reparseFile(file: PsiFile, newText: CharSequence) {
    val node = file.node as? FileElement ?: throw IllegalStateException("${file.javaClass} ${file.fileType}")
    val range = ChangedPsiRangeUtil.getChangedPsiRange(file, node, newText) ?: return
    val indicator = ProgressManager.getGlobalProgressIndicator() ?: EmptyProgressIndicator()
    val log = BlockSupport.getInstance(file.project).reparseRange(file, node, range, newText, indicator, file.viewProvider.contents)

    ProgressManager.getInstance().executeNonCancelableSection { log.doActualPsiChange(file) }
  }

}
