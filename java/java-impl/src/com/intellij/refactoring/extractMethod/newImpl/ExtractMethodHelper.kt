// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.Nullability
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.codeInsight.intention.AddAnnotationPsiFix
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.source.DummyHolder
import com.intellij.psi.impl.source.codeStyle.JavaCodeStyleManagerImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput.*
import com.intellij.refactoring.util.RefactoringUtil

object ExtractMethodHelper {

  fun wrapWithCodeBlock(elements: List<PsiStatement>): List<PsiCodeBlock> {
    require(elements.isNotEmpty())
    val codeBlock = PsiElementFactory.getInstance(elements.first().project).createCodeBlock()
    elements.forEach { codeBlock.add(it) }
    return listOf(codeBlock)
  }

  fun PsiElement.addSiblingAfter(element: PsiElement): PsiElement {
    return this.parent.addAfter(element, this)
  }

  fun getValidParentOf(element: PsiElement): PsiElement {
    val parent = element.parent
    val physicalParent = when (parent) {
      is DummyHolder -> parent.context
      null -> element.context
      else -> parent
    }
    return physicalParent ?: throw IllegalArgumentException()
  }

  fun addNullabilityAnnotation(owner: PsiModifierListOwner, nullability: Nullability) {
    val nullabilityManager = NullableNotNullManager.getInstance(owner.project)
    val annotation = when (nullability) {
      Nullability.NOT_NULL -> nullabilityManager.defaultNotNull
      Nullability.NULLABLE -> nullabilityManager.defaultNullable
      else -> return
    }
    val modifierList = owner.modifierList ?: return
    val annotationElement = AddAnnotationPsiFix.addPhysicalAnnotation(annotation, PsiNameValuePair.EMPTY_ARRAY, modifierList)
    JavaCodeStyleManager.getInstance(owner.project).shortenClassReferences(annotationElement)
  }

  fun guessName(expression: PsiExpression): String? {
    val codeStyleManager = JavaCodeStyleManager.getInstance(expression.project) as JavaCodeStyleManagerImpl
    val name = codeStyleManager
                 .suggestSemanticNames(expression).firstOrNull()
               ?: PsiTreeUtil.findChildOfType(expression, PsiReferenceExpression::class.java)?.referenceName
               ?: "x"

    return codeStyleManager.suggestUniqueVariableName(name, expression, true)
  }

  fun getExpressionType(expression: PsiExpression): PsiType {
    val type = RefactoringUtil.getTypeByExpressionWithExpectedType(expression)
    return when {
      type != null -> type
      expression.parent is PsiExpressionStatement -> PsiType.VOID
      else -> PsiType.getJavaLangObject(expression.manager, GlobalSearchScope.allScope(expression.project))
    }
  }

  fun areSame(elements: List<PsiElement?>): Boolean {
    val first = elements.firstOrNull()
    return elements.all { element -> areSame(first, element) }
  }

  fun areSame(first: PsiElement?, second: PsiElement?): Boolean {
    return when {
      first != null && second != null -> PsiEquivalenceUtil.areElementsEquivalent(first, second)
      first == null && second == null -> true
      else -> false
    }
  }

  private fun boxedTypeOf(type: PsiType, context: PsiElement): PsiType {
    return (type as? PsiPrimitiveType)?.getBoxedType(context) ?: type
  }

  fun PsiModifierListOwner?.hasExplicitModifier(modifier: String): Boolean {
    return this?.modifierList?.hasExplicitModifier(modifier) == true
  }

  fun DataOutput.withBoxedType(): DataOutput {
    return when (this) {
      is VariableOutput -> copy(type = boxedTypeOf(type, variable))
      is ExpressionOutput -> copy(type = boxedTypeOf(type, returnExpressions.first()))
      ArtificialBooleanOutput, EmptyOutput -> this
    }
  }
}
