// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model

import com.intellij.pom.PomDeclarationSearcher
import com.intellij.pom.PomTarget
import com.intellij.pom.PsiDeclaredTarget
import com.intellij.pom.references.PomService
import com.intellij.psi.PsiElement
import com.intellij.util.Consumer
import com.intellij.util.Processor
import com.intellij.util.SmartList

class PomSymbolDeclarationSearcher : SymbolDeclarationSearcher {

  override fun processDeclarations(element: PsiElement, offsetInElement: Int, processor: Processor<in SymbolDeclaration>): Boolean {
    val targets = SmartList<PomTarget>()
    val consumer = Consumer { target: PomTarget ->
      if (checkPomTargetRange(target, element, offsetInElement)) {
        targets.add(target)
      }
    }
    val project = element.project
    for (extension in PomDeclarationSearcher.EP_NAME.extensions) {
      extension.findDeclarationsAt(element, offsetInElement, consumer)
      for (target in targets) {
        val psi = PomService.convertToPsi(project, target)
        if (!processor.process(PsiElementSymbolDeclaration(psi))) return false
      }
      targets.clear()
    }
    return true
  }

  private fun checkPomTargetRange(target: PomTarget, element: PsiElement, offsetInElement: Int): Boolean {
    target as? PsiDeclaredTarget ?: return true
    val range = target.nameIdentifierRange ?: return true
    val navigationElement = target.navigationElement
    val absoluteRange = range.shiftRight(navigationElement.textRange.startOffset)
    val absoluteOffset = element.textRange.startOffset + offsetInElement
    return absoluteOffset in absoluteRange
  }
}
