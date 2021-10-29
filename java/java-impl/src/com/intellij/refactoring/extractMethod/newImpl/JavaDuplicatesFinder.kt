// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

data class ChangedExpression(val pattern: PsiExpression, val candidate: PsiExpression)

data class Duplicate(val pattern: List<PsiElement>, val candidate: List<PsiElement>, val changedExpressions: List<ChangedExpression>)

class JavaDuplicatesFinder(pattern: List<PsiElement>, private val predefinedChanges: Set<PsiExpression> = emptySet()) {

  companion object {
    fun textRangeOf(range: List<PsiElement>) = TextRange(range.first().textRange.startOffset, range.last().textRange.endOffset)
  }

  private val pattern: List<PsiElement> = pattern.filterNot(::isNoise)

  fun withPredefinedChanges(predefinedChanges: Set<PsiExpression>): JavaDuplicatesFinder {
    return JavaDuplicatesFinder(pattern, this.predefinedChanges + predefinedChanges)
  }

  fun findDuplicates(scope: PsiElement): List<Duplicate> {
    val ignoredElements = HashSet<PsiElement>(pattern)

    val duplicates = mutableListOf<Duplicate>()

    val patternExpression = pattern.singleOrNull() as? PsiExpression
    val visitor = if (patternExpression != null) {
      object : JavaRecursiveElementWalkingVisitor(){
        override fun visitExpression(expression: PsiExpression) {
          if (expression in ignoredElements) return
          val duplicate = createDuplicate(childrenOf(patternExpression), childrenOf(expression))
          if (duplicate != null) {
            duplicates += duplicate.copy(pattern = listOf(patternExpression), candidate = listOf(expression))
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
          val duplicate = createDuplicate(pattern, siblings)
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

  fun createExpressionDuplicate(pattern: PsiExpression, candidate: PsiExpression): Duplicate? {
    return createDuplicate(childrenOf(pattern), childrenOf(candidate))
      ?.copy(pattern = listOf(pattern), candidate = listOf(candidate))
  }

  fun createDuplicate(pattern: List<PsiElement>, candidate: List<PsiElement>): Duplicate? {
    val changedExpressions = ArrayList<ChangedExpression>()
    if (!traverseAndCollectChanges(pattern, candidate, changedExpressions)) return null
    return removeInternalReferences(Duplicate(pattern, candidate, changedExpressions))
  }

  private fun removeInternalReferences(duplicate: Duplicate): Duplicate? {
    val patternDeclarations = duplicate.pattern.flatMap { PsiTreeUtil.findChildrenOfType(it, PsiVariable::class.java) }
    val candidateDeclarations = duplicate.candidate.flatMap { PsiTreeUtil.findChildrenOfType(it, PsiVariable::class.java) }
    val declarationsMapping = patternDeclarations.zip(candidateDeclarations).toMap()

    val changedExpressions = duplicate.changedExpressions.filterNot { (pattern, candidate) ->
      val patternVariable = (pattern as? PsiReferenceExpression)?.resolve()
      val candidateVariable = (candidate as? PsiReferenceExpression)?.resolve()
      if (patternVariable !in declarationsMapping.keys && candidateVariable !in declarationsMapping.values) return@filterNot false
      if (declarationsMapping[patternVariable] != candidateVariable) return null
      return@filterNot true
    }

    if (ExtractMethodHelper.hasReferencesToScope(duplicate.pattern, changedExpressions.map{ change -> change.pattern })){
      return null
    }

    return duplicate.copy(changedExpressions = changedExpressions)
  }

  fun traverseAndCollectChanges(pattern: List<PsiElement>,
                                candidate: List<PsiElement>,
                                changedExpressions: MutableList<ChangedExpression>): Boolean {
    if (candidate.size != pattern.size) return false
    val notEqualElements = pattern.zip(candidate).filterNot { (pattern, candidate) ->
      pattern !in predefinedChanges &&
      isEquivalent(pattern, candidate) &&
      traverseAndCollectChanges(childrenOf(pattern), childrenOf(candidate), changedExpressions)
    }
    if (notEqualElements.any { (pattern, candidate) -> ! canBeReplaced(pattern, candidate) }) return false
    changedExpressions += notEqualElements.map { (pattern, candidate) -> ChangedExpression(pattern as PsiExpression, candidate as PsiExpression) }
    return true
  }

  fun isEquivalent(pattern: PsiElement, candidate: PsiElement): Boolean {
    return when {
      pattern is PsiTypeElement && candidate is PsiTypeElement -> canBeReplaced(pattern.type, candidate.type)
      pattern is PsiReferenceExpression && candidate is PsiReferenceExpression -> pattern.resolve() == candidate.resolve()
      pattern is PsiLiteralExpression && candidate is PsiLiteralExpression -> pattern.text == candidate.text
      pattern.node?.elementType == candidate.node?.elementType -> true
      else -> false
    }
  }

  private fun canBeReplaced(pattern: PsiElement, candidate: PsiElement): Boolean {
    return when {
      pattern.parent is PsiExpressionStatement -> false
      pattern is PsiReferenceExpression && pattern.parent is PsiCall -> false
      pattern is PsiExpression && candidate is PsiExpression -> canBeReplaced(pattern.type, candidate.type)
      else -> false
    }
  }

  private fun canBeReplaced(pattern: PsiType?, candidate: PsiType?): Boolean {
    return pattern != null && pattern != PsiType.VOID && candidate != null && pattern.isAssignableFrom(candidate)
  }

  private fun isOvercomplicated(duplicate: Duplicate): Boolean {
    val singleChangedExpression = duplicate.changedExpressions.singleOrNull()?.pattern ?: return false
    val singleDeclaration = duplicate.pattern.singleOrNull() as? PsiDeclarationStatement
    val variable = singleDeclaration?.declaredElements?.singleOrNull() as? PsiVariable
    return variable?.initializer == singleChangedExpression
  }

}