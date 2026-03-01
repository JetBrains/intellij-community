// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.pom.PomManager
import com.intellij.pom.core.impl.PomModelImpl
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.FileElement

class OffsetsInFile(
  val file: PsiFile,
  val offsets: OffsetMap
) {
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
    val task = replaceInCopy(file.copy() as PsiFile, startOffset, endOffset, replacement)
    return task.ensureUpdatedAndGetNewOffsets()
  }

  fun replaceInCopy(
    fileCopy: PsiFile,
    startOffset: Int,
    endOffset: Int,
    replacement: String,
  ): CopyFileUpdateTask {
    val originalText = offsets.document.immutableCharSequence
    val tempDocument = DocumentImpl(originalText, originalText.contains('\r') || replacement.contains('\r'), true)
    val tempMap = offsets.copyOffsets(tempDocument)
    tempDocument.replaceString(startOffset, endOffset, replacement)

    val copyDocument = fileCopy.viewProvider.document!!
    val node = fileCopy.node as? FileElement
               ?: throw IllegalStateException("Node is not a FileElement ${fileCopy.javaClass.name} / ${fileCopy.fileType} / ${fileCopy.node}")
    val pomModel = PomManager.getModel(file.project) as PomModelImpl
    val applyPsiChange = pomModel.reparseFile(fileCopy, node, tempDocument.immutableCharSequence)
    return CopyFileUpdateTask {
      applyPsiChange?.run()
      OffsetsInFile(fileCopy, tempMap.copyOffsets(copyDocument))
    }
  }
}
