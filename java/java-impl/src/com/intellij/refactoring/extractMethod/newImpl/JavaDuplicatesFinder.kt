// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

data class ChangedExpression(val pattern: PsiExpression, val candidate: PsiExpression)

class Duplicate(val pattern: List<PsiElement>, val candidate: List<PsiElement>, val changedExpressions: List<ChangedExpression>)

class JavaDuplicatesFinder(pattern: List<PsiElement>) {

  companion object {
    fun textRangeOf(range: List<PsiElement>) = TextRange(range.first().textRange.startOffset, range.last().textRange.endOffset)
  }

  private val pattern: List<PsiElement> = pattern.filterNot(::isNoise)

  fun findDuplicates(scope: PsiElement): List<Duplicate> {
    val ignoredElements = pattern.toSet()
    val duplicates = mutableListOf<Duplicate>()
    val visitor = object: JavaRecursiveElementWalkingVisitor() {
      override fun visitStatement(statement: PsiStatement) {
        super.visitStatement(statement)
        if (statement in ignoredElements) return
        val siblings = siblingsOf(statement).take(pattern.size).toList()
        val duplicate = createDuplicate(pattern, siblings)
        if (duplicate != null && ! isOvercomplicated(duplicate)) {
          duplicates += duplicate
        }
      }
    }
    scope.accept(visitor)

    return duplicates
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

  fun createDuplicate(pattern: List<PsiElement>, candidate: List<PsiElement>): Duplicate? {
    val changedExpressions = ArrayList<ChangedExpression>()
    if ( !canBeDuplicate(pattern, candidate, changedExpressions)) return null
    val patternDeclarations = pattern.flatMap { PsiTreeUtil.findChildrenOfType(it, PsiVariable::class.java) }
    val candidateDeclarations = candidate.flatMap { PsiTreeUtil.findChildrenOfType(it, PsiVariable::class.java) }
    val declarationsMapping = patternDeclarations.zip(candidateDeclarations).toMap()

    val result = ArrayList<ChangedExpression>()
    changedExpressions.forEach { (pattern, candidate) ->
      if (pattern is PsiReferenceExpression && candidate is PsiReferenceExpression){
        val variable = pattern.resolve()
        val pairedVariable = declarationsMapping[variable]
        if (pairedVariable == null) {
          result += ChangedExpression(pattern, candidate)
        } else if (pairedVariable != candidate.resolve()) {
          return null
        }
      } else {
        result += ChangedExpression(pattern, candidate)
      }
    }

    return Duplicate(pattern, candidate, result)
  }

  fun canBeDuplicate(pattern: List<PsiElement>, candidate: List<PsiElement>, changedExpressions: MutableList<ChangedExpression>): Boolean {
    if (candidate.size != pattern.size) return false
    val notEqualElements = pattern.zip(candidate).filterNot { (pattern, candidate) ->
      isEquivalent(pattern, candidate) && canBeDuplicate(childrenOf(pattern), childrenOf(candidate), changedExpressions)
    }
    if (notEqualElements.any { (pattern, candidate) -> ! canBeReplaced(pattern, candidate) }) return false
    changedExpressions += notEqualElements.map { (pattern, candidate) -> ChangedExpression(pattern as PsiExpression, candidate as PsiExpression) }
    return true
  }

  private fun isEquivalent(pattern: PsiElement, candidate: PsiElement): Boolean {
    return when {
      pattern is PsiTypeElement && candidate is PsiTypeElement -> pattern.type.isAssignableFrom(candidate.type)
      pattern is PsiReferenceExpression && candidate is PsiReferenceExpression -> pattern.resolve() == candidate.resolve()
      pattern is PsiLiteralExpression && candidate is PsiLiteralExpression -> pattern.text == candidate.text
      pattern.node?.elementType == candidate.node?.elementType -> true
      else -> false
    }
  }

  private fun canBeReplaced(pattern: PsiElement, candidate: PsiElement): Boolean {
    return when {
      pattern !is PsiExpression || candidate !is PsiExpression -> false
      pattern.parent is PsiExpressionStatement -> false
      pattern is PsiReferenceExpression && pattern.parent?.parent is PsiExpressionStatement -> false
      pattern is PsiReferenceExpression && pattern.resolve() is PsiMethod && candidate is PsiReferenceExpression -> pattern.resolve() == candidate.resolve()
      else -> pattern.type.isAssignableFrom(candidate.type)
    }
  }

  private fun PsiType?.isAssignableFrom(candidate: PsiType?): Boolean {
    if (this == null || candidate == null) return false
    return isAssignableFrom(candidate)
  }

  private fun isOvercomplicated(duplicate: Duplicate): Boolean {
    val singleChangedExpression = duplicate.changedExpressions.singleOrNull()?.pattern ?: return false
    val singleDeclaration = duplicate.pattern.singleOrNull() as? PsiDeclarationStatement
    val variable = singleDeclaration?.declaredElements?.singleOrNull() as? PsiVariable
    return variable?.initializer == singleChangedExpression
  }

}