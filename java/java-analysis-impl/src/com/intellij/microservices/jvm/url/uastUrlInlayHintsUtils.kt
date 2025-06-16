// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("UastUrlInlayHintsUtils")
@file:ApiStatus.Experimental

package com.intellij.microservices.jvm.url

import com.intellij.microservices.url.inlay.PsiElementUrlPathInlayHint
import com.intellij.microservices.url.inlay.UrlPathInlayHint
import com.intellij.microservices.url.inlay.UrlPathInlayHintsProviderSemElement
import com.intellij.microservices.url.references.UrlPathReferenceInjector
import com.intellij.microservices.url.references.forbidExpensiveUrlContext
import com.intellij.psi.UastSemProvider
import com.intellij.psi.uastSemElementProvider
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.expressions.UInjectionHost

fun urlInlayHintProvider(injector: UrlPathReferenceInjector<UExpression>): UastSemProvider<UrlPathInlayHintsProviderSemElement> {
  return uastSemElementProvider(listOf(UInjectionHost::class.java, UReferenceExpression::class.java)) { uExpression, _ ->
    val context = forbidExpensiveUrlContext {
      injector.defaultRootContextProvider(uExpression)
        .subContext(injector.toUrlPath(uExpression))
    }

    object : UrlPathInlayHintsProviderSemElement {
      override val inlayHints: List<UrlPathInlayHint>
        get() = listOf(PsiElementUrlPathInlayHint(uExpression.sourcePsi!!, context))
    }
  }
}