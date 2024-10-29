// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/**
 * Represents a pair of expressions which are located in the same place inside the initial code and duplicate
 * and which can be extracted into the same new parameter.
 *
 * @property pattern Parametrized expression inside the initial source code.
 * @property candidate Parametrized expression inside the duplicate.
 */
data class ParametrizedExpression(val pattern: PsiExpression, val candidate: PsiExpression)

data class Duplicate(val pattern: List<PsiElement>, val candidate: List<PsiElement>, val parametrizedExpressions: List<ParametrizedExpression>)

class JavaDuplicatesFinder(pattern: List<PsiElement>, private val parametrizedExpressions: Set<PsiExpression> = emptySet()) {

  companion object {
    private val ELEMENT_IN_PHYSICAL_FILE = Key<PsiElement>("ELEMENT_IN_PHYSICAL_FILE")

    /**
     * Ensures that all [PsiMember] elements in copied [root] will be linked to [PsiMember] in the original [root] element.
     * These links are used to compare references between copied and original files.
     */
    fun linkCopiedClassMembersWithOrigin(root: PsiElement) {
      SyntaxTraverser.psiTraverser(root).filter(PsiMember::class.java).forEach { member ->
        member.putCopyableUserData(ELEMENT_IN_PHYSICAL_FILE, member)
      }
    }

    private fun getElementInPhysicalFile(element: PsiElement): PsiElement? = element.getCopyableUserData(ELEMENT_IN_PHYSICAL_FILE)

  }

  private val pattern: List<PsiElement> = pattern.filterNot(::isNoise)

  fun withParametrizedExpressions(parametrizedExpressions: Set<PsiExpression>): JavaDuplicatesFinder {
    return JavaDuplicatesFinder(pattern, this.parametrizedExpressions + parametrizedExpressions)
  }

  fun findDuplicates(scope: PsiElement): List<Duplicate> {
    val ignoredElements = HashSet<PsiElement>(pattern)

    val duplicates = mutableListOf<Duplicate>()

    val patternExpression = pattern.singleOrNull() as? PsiExpression
    val visitor = if (patternExpression != null) {
      object : JavaRecursiveElementWalkingVisitor(){
        override fun visitExpression(expression: PsiExpression) {
          if (expression in ignoredElements) return
          //skip cases when the whole expression will be detected as a change
          val duplicate = if (areNodesEquivalent(patternExpression, expression)) {
            createDuplicateIfPossible(listOf(patternExpression), listOf(expression))
          } else {
            null
          }
          if (duplicate != null) {
            duplicates += duplicate
          } else {
            super.visitExpression(expression)
          }
        }
      }
    } else {
      object: JavaRecursiveElementWalkingVisitor() {
        override fun visitStatement(statement: PsiStatement) {
          if (statement in ignoredElements) return
          val siblings = siblingsOf(statement).take(pattern.size).toList()
          val duplicate = createDuplicateIfPossible(pattern, siblings)
          if (duplicate != null) {
            duplicates += duplicate
            ignoredElements += duplicate.candidate
          } else {
            super.visitStatement(statement)
          }
        }
      }
    }
    scope.accept(visitor)

    return duplicates.filterNot(::isOvercomplicated)
  }

  private fun isNoise(it: PsiElement) = it is PsiWhiteSpace || it is PsiComment || it is PsiEmptyStatement

  private fun siblingsOf(element: PsiElement?): Sequence<PsiElement> {
    return if (element != null) {
      generateSequence(element) { it.nextSibling }.filterNot(::isNoise)
    } else {
      emptySequence()
    }
  }

  private fun childrenOf(element: PsiElement?): List<PsiElement> {
    return siblingsOf(element?.firstChild).toList()
  }

  fun createDuplicateIfPossible(pattern: List<PsiElement>, candidate: List<PsiElement>): Duplicate? {
    val parametrizedExpressions = ArrayList<ParametrizedExpression>()
    if (!traverseAndCollectChanges(pattern, candidate, parametrizedExpressions)) return null
    return removeInternalReferences(Duplicate(pattern, candidate, parametrizedExpressions))
  }

  private fun removeInternalReferences(duplicate: Duplicate): Duplicate? {
    val patternDeclarations = duplicate.pattern.flatMap { PsiTreeUtil.findChildrenOfType(it, PsiVariable::class.java) }
    val candidateDeclarations = duplicate.candidate.flatMap { PsiTreeUtil.findChildrenOfType(it, PsiVariable::class.java) }
    val declarationsMapping = patternDeclarations.zip(candidateDeclarations).toMap()

    val parametrizedExpressions = duplicate.parametrizedExpressions.filterNot { (pattern, candidate) ->
      val patternVariable = (pattern as? PsiReferenceExpression)?.resolve()
      val candidateVariable = (candidate as? PsiReferenceExpression)?.resolve()
      if (patternVariable !in declarationsMapping.keys && candidateVariable !in declarationsMapping.values) return@filterNot false
      if (declarationsMapping[patternVariable] != candidateVariable) return null
      return@filterNot true
    }

    if (ExtractMethodHelper.hasReferencesToScope(duplicate.pattern, parametrizedExpressions.map{ change -> change.pattern }) ||
        ExtractMethodHelper.hasReferencesToScope(duplicate.candidate, parametrizedExpressions.map { change -> change.candidate })){
      return null
    }

    return duplicate.copy(parametrizedExpressions = parametrizedExpressions)
  }

  /**
   * Does recursive equivalence check for [pattern] and [candidate] nodes.
   * Puts parametrized expressions into [parametrizedExpressions] list.
   * @return false if [pattern] and [candidate] are not parametrized duplicates.
   */
  private fun traverseAndCollectChanges(pattern: List<PsiElement>,
                                        candidate: List<PsiElement>,
                                        parametrizedExpressions: MutableList<ParametrizedExpression>): Boolean {
    if (candidate.size != pattern.size) return false
    val notEqualElements = pattern.zip(candidate).filterNot { (pattern, candidate) ->
      pattern !in this.parametrizedExpressions &&
      areNodesEquivalent(pattern, candidate) &&
      traverseAndCollectChanges(childrenOf(pattern), childrenOf(candidate), parametrizedExpressions)
    }
    if (notEqualElements.any { (pattern, candidate) -> ! canBeReplaced(pattern, candidate) }) return false
    parametrizedExpressions += notEqualElements.map { (pattern, candidate) -> ParametrizedExpression(pattern as PsiExpression, candidate as PsiExpression) }
    return true
  }

  /**
   * Non-recursive equivalence check for [pattern] and [candidate] elements.
   */
  private fun areNodesEquivalent(pattern: PsiElement, candidate: PsiElement): Boolean {
    return when {
      pattern is PsiTypeElement && candidate is PsiTypeElement -> canBeReplaced(pattern.type, candidate.type)
      pattern is PsiJavaCodeReferenceElement && candidate is PsiJavaCodeReferenceElement ->
        areElementsEquivalent(pattern.resolve(), candidate.resolve())
      pattern is PsiLiteralExpression && candidate is PsiLiteralExpression -> pattern.text == candidate.text
      pattern.node?.elementType == candidate.node?.elementType -> true
      else -> false
    }
  }

  private fun areElementsEquivalent(pattern: PsiElement?, candidate: PsiElement?): Boolean {
    if (pattern?.isValid != true || candidate?.isValid != true) return false
    return pattern.manager.areElementsEquivalent(getElementInPhysicalFile(pattern) ?: pattern, candidate)
  }

  private fun canBeReplaced(pattern: PsiElement, candidate: PsiElement): Boolean {
    return when {
      pattern.parent is PsiExpressionStatement -> false
      pattern is PsiReferenceExpression && pattern.parent is PsiCall -> false
      pattern is PsiExpression && candidate is PsiExpression -> pattern.type != PsiTypes.voidType() && canBeReplaced(pattern.type, candidate.type)
      else -> false
    }
  }

  private fun canBeReplaced(pattern: PsiType?, candidate: PsiType?): Boolean {
    if (pattern == null || candidate == null) return false
    if (pattern is PsiDiamondType && candidate is PsiDiamondType) {
      val patternTypes = getInferredTypes(pattern) ?: return false
      val candidateTypes = getInferredTypes(candidate) ?: return false
      if (patternTypes.size != candidateTypes.size) return false
      return patternTypes.indices.all { i -> canBeReplaced(patternTypes[i], candidateTypes[i]) }
    }
    return pattern.isAssignableFrom(candidate)
  }

  private fun getInferredTypes(diamondType: PsiDiamondType): List<PsiType>? {
    return diamondType.resolveInferredTypes()?.takeIf { resolveResult -> !resolveResult.failedToInfer() }?.inferredTypes
  }

  private fun isOvercomplicated(duplicate: Duplicate): Boolean {
    val singleChangedExpression = duplicate.parametrizedExpressions.singleOrNull()?.pattern ?: return false
    val singleDeclaration = duplicate.pattern.singleOrNull() as? PsiDeclarationStatement
    val variable = singleDeclaration?.declaredElements?.singleOrNull() as? PsiVariable
    return variable?.initializer == singleChangedExpression
  }

}