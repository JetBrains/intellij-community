// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("Declarations")

package com.intellij.model.psi.impl

import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolDeclarationProvider
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.util.elementsAroundOffsetUp
import com.intellij.util.SmartList
import org.jetbrains.annotations.TestOnly

/**
 * @return collection of declarations found around the given [offset][offsetInFile] in [file][this]
 */
internal fun PsiFile.allDeclarationsAround(offsetInFile: Int): Collection<PsiSymbolDeclaration> {
  for ((element: PsiElement, offsetInElement: Int) in elementsAroundOffsetUp(offsetInFile)) {
    ProgressManager.checkCanceled()
    val declarations: Collection<PsiSymbolDeclaration> = declarationsInElement(element, offsetInElement)
    if (declarations.isNotEmpty()) {
      return declarations
    }
  }
  return emptyList()
}

/**
 * @return `true` if any declaration intersects with [[startOffsetInElement], [endOffsetInElement]), otherwise `false`
 * @see hasReferencesInElement
 */
internal fun hasDeclarationsInElement(element: PsiElement, startOffsetInElement: Int, endOffsetInElement: Int): Boolean {
  val declarationsInElement = declarationsInElement(element, -1)
  for (declaration in declarationsInElement) {
    if (declaration.rangeInDeclaringElement.intersects(startOffsetInElement, endOffsetInElement)) {
      return true
    }
  }
  return false
}

internal fun allDeclarationsInElement(element: PsiElement): Collection<PsiSymbolDeclaration> = declarationsInElement(element, -1)

private val declarationProviderEP = ExtensionPointName<PsiSymbolDeclarationProvider>("com.intellij.psi.declarationProvider")

private fun declarationsInElement(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolDeclaration> {
  val result = SmartList<PsiSymbolDeclaration>()
  result.addAll(element.ownDeclarations)
  for (extension: PsiSymbolDeclarationProvider in declarationProviderEP.lazySequence()) {
    ProgressManager.checkCanceled()
    result.addAll(extension.getDeclarations(element, offsetInElement))
  }
  return result.filterTo(SmartList()) {
    element === it.declaringElement && (offsetInElement < 0 || it.rangeInDeclaringElement.containsOffset(offsetInElement))
  }
}

@TestOnly
fun PsiFile.allDeclarations(): Collection<PsiSymbolDeclaration> {
  val declarations = ArrayList<PsiSymbolDeclaration>()
  accept(DeclarationCollectingVisitor(declarations))
  return declarations
}

private class DeclarationCollectingVisitor(
  private val declarations: MutableList<PsiSymbolDeclaration>
) : PsiRecursiveElementWalkingVisitor(true) {

  override fun visitElement(element: PsiElement) {
    super.visitElement(element)
    declarations.addAll(declarationsInElement(element, -1))
  }
}
