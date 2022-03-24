// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi.impl

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.diagnostic.PluginException
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiSymbolDeclaration
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.leavesAroundOffset
import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Entry point for obtaining target symbols by [offset] in a [file].
 *
 * @return collection of referenced or declared symbols
 */
@Experimental
fun targetSymbols(file: PsiFile, offset: Int): Collection<Symbol> {
  val (declaredData, referencedData) = declaredReferencedData(file, offset)
                                       ?: return emptyList()
  val data = referencedData
             ?: declaredData
             ?: return emptyList()
  return data.targets.map { it.symbol }
}

/**
 * @return two collections: of declared and of referenced symbols
 */
@Experimental
fun targetDeclarationAndReferenceSymbols(file: PsiFile, offset: Int): Pair<Collection<Symbol>, Collection<Symbol>> {
  val (declaredData, referencedData) = declaredReferencedData(file, offset) ?: return Pair(emptyList(), emptyList())
  return (declaredData?.targets?.map { it.symbol } ?: emptyList()) to (referencedData?.targets?.map { it.symbol } ?: emptyList())
}

internal fun declaredReferencedData(file: PsiFile, offset: Int): DeclaredReferencedData? {
  val allDeclarationsOrReferences: List<DeclarationOrReference> = declarationsOrReferences(file, offset)
  if (allDeclarationsOrReferences.isEmpty()) {
    return null
  }

  val withMinimalRanges: Collection<DeclarationOrReference> = try {
    chooseByRange(allDeclarationsOrReferences, offset, DeclarationOrReference::rangeWithOffset)
  }
  catch (e: RangeOverlapException) {
    val details = allDeclarationsOrReferences.joinToString(separator = "") { item ->
      "\n${item.rangeWithOffset} : $item"
    }
    LOG.error("Range overlap", PluginException.createByClass(e, file.javaClass), details)
    return null
  }

  val declarations = SmartList<DeclarationOrReference.Declaration>()
  val references = SmartList<DeclarationOrReference.Reference>()

  for (dr in withMinimalRanges) {
    when (dr) {
      is DeclarationOrReference.Declaration -> declarations.add(dr)
      is DeclarationOrReference.Reference -> references.add(dr)
    }
  }

  return DeclaredReferencedData(
    declaredData = declarations.takeUnless { it.isEmpty() }?.let(TargetData::Declared),
    referencedData = references.takeUnless { it.isEmpty() }?.let(TargetData::Referenced)
  )
}

internal sealed class DeclarationOrReference {

  abstract val rangeWithOffset: TextRange

  abstract val ranges: List<TextRange>

  class Declaration(val declaration: PsiSymbolDeclaration) : DeclarationOrReference() {

    override val rangeWithOffset: TextRange get() = declaration.absoluteRange

    override val ranges: List<TextRange> get() = listOf(declaration.absoluteRange)

    override fun toString(): String = declaration.toString()
  }

  class Reference(val reference: PsiSymbolReference, private val offset: Int) : DeclarationOrReference() {

    override val rangeWithOffset: TextRange by lazy(LazyThreadSafetyMode.NONE) {
      referenceRanges(reference).find {
        it.containsOffset(offset)
      } ?: error("One of the ranges must contain offset at this point")
    }

    override val ranges: List<TextRange> get() = referenceRanges(reference)

    override fun toString(): String = reference.toString()
  }
}

private fun referenceRanges(it: PsiSymbolReference): List<TextRange> {
  return if (it is EvaluatorReference) {
    it.origin.absoluteRanges
  }
  else {
    // Symbol references don't support multi-ranges yet.
    listOf(it.absoluteRange)
  }
}

/**
 * @return declarations/references which contain the given [offset] in the [file]
 */
private fun declarationsOrReferences(file: PsiFile, offset: Int): List<DeclarationOrReference> {
  val result = SmartList<DeclarationOrReference>()

  var foundNamedElement: PsiElement? = null

  val allDeclarations = file.allDeclarationsAround(offset)
  if (allDeclarations.isEmpty()) {
    namedElement(file, offset)?.let { (namedElement, leaf) ->
      foundNamedElement = namedElement
      val declaration: PsiSymbolDeclaration = PsiElement2Declaration.createFromDeclaredPsiElement(namedElement, leaf)
      result += DeclarationOrReference.Declaration(declaration)
    }
  }
  else {
    allDeclarations.mapTo(result, DeclarationOrReference::Declaration)
  }

  val allReferences = file.allReferencesAround(offset)
  if (allReferences.isEmpty()) {
    fromTargetEvaluator(file, offset)?.let { evaluatorReference ->
      if (foundNamedElement != null && evaluatorReference.targetElements.singleOrNull() === foundNamedElement) {
        return@let // treat self-reference as a declaration
      }
      result += DeclarationOrReference.Reference(evaluatorReference, offset)
    }
  }
  else {
    allReferences.mapTo(result) { DeclarationOrReference.Reference(it, offset) }
  }

  return result
}

private data class NamedElementAndLeaf(val namedElement: PsiElement, val leaf: PsiElement)

private fun namedElement(file: PsiFile, offset: Int): NamedElementAndLeaf? {
  for ((leaf, _) in file.leavesAroundOffset(offset)) {
    val namedElement: PsiElement? = TargetElementUtil.getNamedElement(leaf)
    if (namedElement != null) {
      return NamedElementAndLeaf(namedElement, leaf)
    }
  }
  return null
}

private fun fromTargetEvaluator(file: PsiFile, offset: Int): EvaluatorReference? {
  val editor = mockEditor(file) ?: return null
  val flags = TargetElementUtil.getInstance().allAccepted and
    TargetElementUtil.ELEMENT_NAME_ACCEPTED.inv() and
    TargetElementUtil.LOOKUP_ITEM_ACCEPTED.inv()
  val reference = TargetElementUtil.findReference(editor, offset)
  val origin: PsiOrigin = if (reference != null) {
    PsiOrigin.Reference(reference)
  }
  else {
    val leaf = file.findElementAt(TargetElementUtil.adjustOffset(file, editor.document, offset)) ?: return null
    PsiOrigin.Leaf(leaf)
  }
  val targetElement = TargetElementUtil.getInstance().findTargetElement(editor, flags, offset)
  val targetElements: List<PsiElement> = when {
    targetElement != null -> listOf(targetElement)
    reference != null -> TargetElementUtil.getInstance().getTargetCandidates(reference).toList()
    else -> emptyList()
  }
  if (targetElements.isEmpty()) {
    return null
  }
  return EvaluatorReference(origin, targetElements)
}
