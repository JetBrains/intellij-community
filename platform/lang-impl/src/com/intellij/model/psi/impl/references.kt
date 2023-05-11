// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi.impl

import com.intellij.model.Symbol
import com.intellij.model.psi.ImplicitReferenceProvider
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.util.elementsAroundOffsetUp
import com.intellij.psi.util.elementsAtOffsetUp
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly

/**
 * @return collection of [references][PsiSymbolReferenceService.getReferences] to the right of given [offset]
 */
@Internal
fun PsiFile.referencesAt(offset: Int): Collection<PsiSymbolReference> {
  for ((element, offsetInElement) in elementsAtOffsetUp(offset)) {
    val references = referencesInElement(element, offsetInElement)
    if (references.isNotEmpty()) {
      return references
    }
  }
  return emptyList()
}

/**
 * @return collection of [references][PsiSymbolReferenceService.getReferences]
 * and [implicit references][ImplicitReferenceProvider] around given [offset]
 */
internal fun PsiFile.allReferencesAround(offset: Int): Collection<PsiSymbolReference> {
  for ((element, offsetInElement) in elementsAroundOffsetUp(offset)) {
    val referencesInElement = allReferencesInElement(element, offsetInElement)
    if (referencesInElement.isNotEmpty()) {
      return referencesInElement
    }
  }
  return emptyList()
}

/**
 * @return `true` if any reference intersects with [[startOffsetInElement], [endOffsetInElement]), otherwise `false`
 * @see hasDeclarationsInElement
 */
internal fun hasReferencesInElement(element: PsiElement, startOffsetInElement: Int, endOffsetInElement: Int): Boolean {
  val referencesInElement = allReferencesInElement(element, -1)
  for (reference in referencesInElement) {
    if (reference.rangeInElement.intersects(startOffsetInElement, endOffsetInElement)) {
      return true
    }
  }
  return false
}

private fun allReferencesInElement(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolReference> {
  val references: Collection<PsiSymbolReference> = referencesInElement(element, offsetInElement)
  if (references.isNotEmpty()) {
    return references
  }
  val implicitReference = implicitReference(element)
  if (implicitReference != null) {
    return listOf(implicitReference)
  }
  return emptyList()
}

private fun referencesInElement(element: PsiElement, offsetInElement: Int): Collection<PsiSymbolReference> {
  if (offsetInElement < 0) {
    return PsiSymbolReferenceService.getService().getReferences(element)
  }
  else {
    val hints = PsiSymbolReferenceHints.offsetHint(offsetInElement)
    return PsiSymbolReferenceService.getService().getReferences(element, hints)
  }
}

private fun implicitReference(element: PsiElement): PsiSymbolReference? {
  for (handler in ImplicitReferenceProvider.EP_NAME.extensions) {
    val resolved = handler.resolveAsReference(element)
    if (resolved.isNotEmpty()) {
      return ImmediatePsiSymbolReference(element, resolved)
    }
  }
  return null
}

@TestOnly
fun PsiFile.allReferences(): Collection<PsiSymbolReference> {
  val references = ArrayList<PsiSymbolReference>()
  accept(ReferenceCollectingVisitor(references))
  return references
}

private class ReferenceCollectingVisitor(
  private val references: MutableList<PsiSymbolReference>
) : PsiRecursiveElementWalkingVisitor(true) {

  override fun visitElement(element: PsiElement) {
    super.visitElement(element)
    references.addAll(allReferencesInElement(element, -1))
  }
}

private class ImmediatePsiSymbolReference(
  private val myElement: PsiElement,
  private val myTargets: Collection<Symbol>
) : PsiSymbolReference {

  private val myRange = TextRange.from(0, element.textLength)

  override fun getElement(): PsiElement = myElement
  override fun getRangeInElement(): TextRange = myRange
  override fun resolveReference(): Collection<Symbol> = myTargets
}
