// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.util.Processor
import com.intellij.util.SmartList

class SymbolServiceImpl : SymbolService {

  override fun processAllDeclarations(file: PsiFile, offset: Int, processor: Processor<in SymbolDeclaration>): Boolean {
    var currentElement: PsiElement? = file.findElementAt(offset) ?: return true
    while (currentElement != null && currentElement !is PsiFileSystemItem) {
      val offsetInElement = offset - currentElement.textRange.startOffset
      for (searcher in SymbolDeclarationSearcher.EP_NAME.extensions) {
        if (!searcher.processDeclarations(currentElement, offsetInElement, processor)) {
          return false
        }
      }
      currentElement = currentElement.parent
    }
    return true
  }

  override fun findAllDeclarations(file: PsiFile, offset: Int): Collection<SymbolDeclaration> {
    val result = SmartList<SymbolDeclaration>()
    processAllDeclarations(file, offset) { result.add(it); true }
    return result
  }

  override fun findDeclarationAt(file: PsiFile, offset: Int): SymbolDeclaration? {
    var result: SymbolDeclaration? = null
    processAllDeclarations(file, offset) { result = it; false }
    return result
  }
}
