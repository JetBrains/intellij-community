// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("TestOnlyProblems")

package com.intellij.lang.documentation.impl

import com.intellij.lang.documentation.*
import com.intellij.model.Pointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

internal fun DocumentationTarget.documentationRequest(): DocumentationRequest {
  ApplicationManager.getApplication().assertReadAccessAllowed()
  return DocumentationRequest(createPointer(), presentation)
}

@ApiStatus.Internal
fun CoroutineScope.computeDocumentationAsync(targetPointer: Pointer<out DocumentationTarget>): Deferred<DocumentationData?> {
  return async(Dispatchers.Default) {
    val documentationResult: DocumentationResult? = readAction {
      targetPointer.dereference()?.computeDocumentation()
    }
    @Suppress("REDUNDANT_ELSE_IN_WHEN")
    when (documentationResult) {
      is DocumentationData -> documentationResult
      is AsyncDocumentation -> documentationResult.supplier.invoke() as DocumentationData?
      null -> null
      else -> error("Unexpected result: $documentationResult") // this fixes Kotlin incremental compilation
    }
  }
}

@ApiStatus.Internal
suspend fun resolveLink(targetPointer: Pointer<out DocumentationTarget>, url: String): InternalLinkResult {
  return withContext(Dispatchers.Default) {
    readAction {
      doResolveLink(targetPointer, url)
    }
  }
}

private fun doResolveLink(targetPointer: Pointer<out DocumentationTarget>, url: String): InternalLinkResult {
  val target = targetPointer.dereference() ?: return InternalLinkResult.InvalidTarget
  val result = resolveLink(target, url) ?: return InternalLinkResult.CannotResolve
  return InternalLinkResult.OK(result.target.documentationRequest())
}

internal fun resolveLink(target: DocumentationTarget, url: String): LinkResult? {
  for (handler in DocumentationLinkResolver.EP_NAME.extensionList) {
    ProgressManager.checkCanceled()
    return handler.resolveLink(target, url) ?: continue
  }
  return null
}

@TestOnly
fun computeDocumentation(targetPointer: Pointer<out DocumentationTarget>): DocumentationData? {
  return runBlockingCancellable {
    withContext(Dispatchers.Default) {
      computeDocumentationAsync(targetPointer).await()
    }
  }
}
