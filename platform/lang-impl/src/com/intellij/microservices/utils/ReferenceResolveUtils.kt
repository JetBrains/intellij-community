// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.utils

import com.intellij.microservices.url.references.UrlPathReference
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CancellationCheck
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiElement
import com.intellij.util.lazyPub

inline fun <T> lazySynchronousResolve(@NlsContexts.ProgressTitle statusMessage: String, crossinline provider: () -> T): Lazy<T> = lazyPub {
  if (ApplicationManager.getApplication().isDispatchThread) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously<T, Exception>(ThrowableComputable {
      ReadAction.compute(ThrowableComputable<T, Exception> { CancellationCheck.runWithCancellationCheck { provider.invoke() } })
    }, statusMessage, true, null)
  }
  else provider.invoke()
}

fun getLastUrlPathReference(psiElement: PsiElement): UrlPathReference? =
  psiElement.references
    .filterIsInstance<UrlPathReference>()
    .sortedBy { it.rangeInElement.endOffset }
    .firstOrNull { it.isAtEnd }

fun getFakePomTargetForLastUrlPathReference(psiElement: PsiElement): PsiElement? =
  getLastUrlPathReference(psiElement)
    ?.multiResolve(false)
    ?.firstOrNull()
    ?.element