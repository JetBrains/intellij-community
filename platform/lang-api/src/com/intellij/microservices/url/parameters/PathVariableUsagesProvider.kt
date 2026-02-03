package com.intellij.microservices.url.parameters

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement

/**
 * An interface that allows frameworks to provide additional info about PathVariable usage,
 * especially for cases when usages could not be located by generic Find Usages
 *
 * @see com.intellij.microservices.url.parameters.PathVariableSem
 */
interface PathVariableUsagesProvider : SemDefinitionProvider {

  /**
   * Suggests names of already used Path Variables in the current context to help user declare a new Path Variable
   */
  fun getCompletionVariantsForDeclaration(context: PsiElement): Iterable<LookupElement>
}

open class DefaultPathVariableUsagesProvider : PathVariableUsagesProvider {
  override fun getCompletionVariantsForDeclaration(context: PsiElement): Iterable<LookupElement> = emptyList()
  override fun findSemDefiningElements(pomTarget: PathVariablePomTarget): Iterable<PsiElement> = emptyList()
}