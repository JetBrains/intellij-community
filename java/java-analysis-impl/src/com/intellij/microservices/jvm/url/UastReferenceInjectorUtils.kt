// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("UastReferenceInjectorUtils")

package com.intellij.microservices.jvm.url

import com.intellij.microservices.url.references.UrlPathReferenceInjector
import com.intellij.microservices.url.references.UrlPksParser
import com.intellij.microservices.url.references.UrlSegmentReferenceTarget
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.UastReferenceProvider
import com.intellij.psi.getRequestedPsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UStringConcatenationsFacade

@JvmOverloads
public fun uastUrlPathReferenceInjectorForScheme(schemes: List<String>,
                                                 parser: UrlPksParser = UrlPksParser()): UrlPathReferenceInjector<UExpression> =
  UrlPathReferenceInjector.forPartialStringFrom<UExpression>(parser) { uElement ->
    val context = getContextExpression(uElement) ?: return@forPartialStringFrom null
    UStringConcatenationsFacade.createFromUExpression(context)?.asPartiallyKnownString()
  }.withSchemesSupport(schemes)

@ApiStatus.Internal
public fun getContextExpression(uExpression: UExpression): UExpression? =
  uExpression.withContainingElements.take(2).firstOrNull { child ->
    child.uastParent.let {
      it is UCallExpression
      || it is UNamedExpression
      || it is UVariable
      || it is UBinaryExpression && it.operator == UastBinaryOperator.ASSIGN
      || (it is UQualifiedReferenceExpression && it.selector is UCallExpression)
    }
  } as? UExpression

public fun uastUrlReferenceProvider(schemes: List<String>): UastReferenceProvider {
  return uastUrlReferenceProvider(
    uastUrlPathReferenceInjectorForScheme(schemes))
}

public fun uastUrlReferenceProvider(injector: UrlPathReferenceInjector<UExpression>): UastReferenceProvider {
  return UastUrlPathReferenceProvider { uExpression, psiElement ->
    injector.buildReferences(uExpression).forPsiElement(psiElement)
  }
}

/**
 * This provider implements performance-optimisation in order to not contribute UrlPath references when the target element is known and
 * it is not UrlSegmentReferenceTarget.
 */
public class UastUrlPathReferenceProvider(public val provider: (UExpression, PsiElement) -> Array<PsiReference>)
  : UastReferenceProvider(UExpression::class.java) {

  override fun getReferencesByElement(element: UElement, context: ProcessingContext): Array<PsiReference> {
    return provider(UExpression::class.java.cast(element), getRequestedPsiElement(context))
  }

  override fun acceptsTarget(target: PsiElement): Boolean {
    return target is UrlSegmentReferenceTarget
  }

  override fun toString(): String = "UastUrlPathReferenceProvider($provider)"
}