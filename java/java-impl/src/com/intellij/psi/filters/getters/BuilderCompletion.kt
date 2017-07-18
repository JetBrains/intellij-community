/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.filters.getters

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.JavaMethodCallElement
import com.intellij.codeInsight.lookup.ExpressionLookupItem
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.IncorrectOperationException
import com.intellij.util.PlatformIcons
import java.util.*

/**
 * @author peter
 */

internal class BuilderCompletion(private val expectedType: PsiClassType, private val expectedClass: PsiClass, private val place: PsiElement) {
  fun suggestBuilderVariants(): List<LookupElement> {
    val result = ArrayList<LookupElement>()
    for (builderClass in expectedClass.innerClasses.filter { it.name?.contains("Builder") == true }) {
      for (buildMethods in methodsReturning(expectedClass, builderClass, true)) {
        for (createMethods in methodsReturning(builderClass, expectedClass, false)) {
          createBuilderCall(buildMethods, createMethods)?.let { result.add(it) }
        }
      }
    }
    return result
  }

  private fun methodsReturning(containingClass: PsiClass, returnedClass: PsiClass, isStatic: Boolean): Collection<List<PsiMethod>> {
    return containingClass.allMethodsAndTheirSubstitutors
      .filter { it.first.hasModifierProperty(PsiModifier.STATIC) == isStatic &&
                returnedClass == PsiUtil.resolveClassInClassTypeOnly(it.second.substitute(it.first.returnType)) &&
                PsiUtil.isAccessible(it.first, place, null) }
      .map { it.first }
      .groupBy { it.name }
      .values
  }

  private fun showOverloads(methods: List<PsiMethod>) = if (methods.any { it.parameterList.parametersCount > 0 }) "..." else ""

  private fun createBuilderCall(buildOverloads: List<PsiMethod>, createOverloads: List<PsiMethod>): LookupElement? {
    val classQname = expectedClass.qualifiedName ?: return null
    val classShortName = expectedClass.name ?: return null

    val buildName = buildOverloads.first().name
    val createName = createOverloads.first().name

    val typeArgs = JavaMethodCallElement.getTypeParamsText(false, expectedClass, expectedType.resolveGenerics().substitutor) ?: ""
    val canonicalText = "$classQname.$typeArgs$buildName().$createName()"
    val presentableText = "$classShortName.$buildName(${showOverloads(buildOverloads)}).$createName(${showOverloads(createOverloads)})"

    val expr =
      try { JavaPsiFacade.getElementFactory(expectedClass.project).createExpressionFromText(canonicalText, place) }
      catch(e: IncorrectOperationException) { return null }

    if (expr.type != expectedType) return null

    return object: ExpressionLookupItem(expr, PlatformIcons.METHOD_ICON, presentableText, canonicalText, presentableText, buildName, createName) {
      override fun handleInsert(context: InsertionContext) {
        super.handleInsert(context)
        positionCaret(context)
      }
    }
  }

}

private fun positionCaret(context: InsertionContext) {
  val createCall = PsiTreeUtil.findElementOfClassAtOffset(context.file, context.tailOffset - 1, PsiMethodCallExpression::class.java, false)
  val buildCall = createCall?.methodExpression?.qualifierExpression as? PsiMethodCallExpression ?: return

  val hasParams = buildCall.methodExpression.multiResolve(true).any { ((it.element as? PsiMethod)?.parameterList?.parametersCount ?: 0) > 0 }
  val argRange = buildCall.argumentList.textRange
  context.editor.caretModel.moveToOffset(if (hasParams) (argRange.startOffset + argRange.endOffset) / 2 else argRange.endOffset)
}

