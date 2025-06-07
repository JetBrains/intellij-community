// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.jvm.url

import com.intellij.lang.Language
import com.intellij.microservices.url.inlay.UrlPathInlayLanguagesProvider
import com.intellij.psi.PsiElement
import com.intellij.uast.UastMetaLanguage
import org.jetbrains.uast.*
import org.jetbrains.uast.UastFacade.getPossiblePsiSourceTypes

private const val SEARCH_LIMIT = 3

internal class UastUrlPathInlayLanguagesProvider : UrlPathInlayLanguagesProvider {

  private val jamPsiSourceTypes = getPossiblePsiSourceTypes(UClass::class.java, UMethod::class.java)

  private val blockedUExpressionTypes: Collection<Class<*>> = listOf(
    UQualifiedReferenceExpression::class.java,
    UClassLiteralExpression::class.java,
    ULambdaExpression::class.java,
    UJumpExpression::class.java,
    UIfExpression::class.java,
    ULoopExpression::class.java,
    UTypeReferenceExpression::class.java
  )

  override val languages: Collection<Language>
    get() = Language.findInstance(UastMetaLanguage::class.java).matchingLanguages

  override fun getPotentialElementsWithHintsProviders(element: PsiElement): List<PsiElement> {
    // for JAM
    if (jamPsiSourceTypes.contains(element.javaClass)) { // do not try to convert every element in big files
      val uDeclaration = element.toUElementOfExpectedTypes(UClass::class.java, UMethod::class.java)
      if (uDeclaration != null) {
        if (uDeclaration.sourcePsi != element) return emptyList()

        val javaPsiElement = uDeclaration.javaPsi
        if (element == javaPsiElement) {
          return listOf(element)
        }
        return listOfNotNull(element, javaPsiElement)
      }
    }

    val uExpression = element.toUElementOfType<UExpression>() ?: return emptyList()
    if (blockedUExpressionTypes.any { it.isAssignableFrom(uExpression.javaClass) }) {
       // performance optimization: do not query SEM for `receiver.selector()`, `() -> {}`, control flow expressions
       return emptyList()
    }

    // currently arguments of call expressions and values of fields are supported
    if (hasFieldOrExpressionParent(uExpression)) {
      val uastParent = uExpression.uastParent
      if (uastParent is UTypeReferenceExpression
          || uastParent is UCallableReferenceExpression
          || uExpression is ULiteralExpression && (uExpression.isBoolean || uExpression.isNull)) {
        // no URLs inlay in types, booleans and nulls
        return emptyList()
      }

      return listOf(element)
    }

    return emptyList()
  }

  private fun hasFieldOrExpressionParent(uExpression: UExpression): Boolean {
    if (uExpression.getUCallExpression(searchLimit = SEARCH_LIMIT) != null) return true

    var u: UElement = uExpression
    repeat(SEARCH_LIMIT) {
      if (u is UField) return true
      u = u.uastParent ?: return false
    }

    return false
  }
}