/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.completion

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil

/**
 * @author peter
 */
class OffsetsInFile(val file: PsiFile, val offsets: OffsetMap) {
  constructor(file: PsiFile) : this(file, OffsetMap(file.viewProvider.document!!))

  fun toTopLevelFile(): OffsetsInFile {
    val manager = InjectedLanguageManager.getInstance(file.project)
    val hostFile = manager.getTopLevelFile(file)
    return if (hostFile == file) this else mapOffsets(hostFile) { manager.injectedToHost(file, it) }
  }

  fun toInjectedIfAny(offset: Int): OffsetsInFile {
    val injected = InjectedLanguageUtil.findInjectedPsiNoCommit(file, offset) ?: return this
    val documentWindow = InjectedLanguageUtil.getDocumentWindow(injected)!!
    return mapOffsets(injected) { documentWindow.hostToInjected(it) }
  }

  fun toFileCopy(copyFile: PsiFile): OffsetsInFile {
    CompletionAssertions.assertCorrectOriginalFile("Given ", file, copyFile)
    assert(copyFile.viewProvider.document!!.textLength == file.viewProvider.document!!.textLength)
    return mapOffsets(copyFile) { it }
  }

  private fun mapOffsets(newFile: PsiFile, offsetFun: (Int) -> Int): OffsetsInFile {
    val map = OffsetMap(newFile.viewProvider.document!!)
    for (key in offsets.allOffsets) {
      map.addOffset(key, offsetFun(offsets.getOffset(key)))
    }
    return OffsetsInFile(newFile, map)
  }

  fun copyWithReplacement(startOffset: Int, endOffset: Int, replacement: String): OffsetsInFile {
    val fileCopy = file.copy() as PsiFile
    val document = fileCopy.viewProvider.document!!
    document.setText(file.viewProvider.document!!.immutableCharSequence) // original file might be uncommitted

    val result = toFileCopy(fileCopy)
    document.replaceString(startOffset, endOffset, replacement)

    PsiDocumentManager.getInstance(file.project).commitDocument(document)

    return result
  }

}
