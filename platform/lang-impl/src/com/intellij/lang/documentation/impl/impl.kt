// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
suspend fun handleLink(targetPointer: Pointer<out DocumentationTarget>, url: String): InternalLinkResult {
  return withContext(Dispatchers.Default) {
    readAction {
      doHandleLink(targetPointer, url)
    }
  }
}

private fun doHandleLink(targetPointer: Pointer<out DocumentationTarget>, url: String): InternalLinkResult {
  val target = targetPointer.dereference()
               ?: return InternalLinkResult.InvalidTarget
  return tryResolveLink(target, url)
         ?: tryContentUpdater(target, url)
         ?: InternalLinkResult.CannotResolve
}

private fun tryResolveLink(target: DocumentationTarget, url: String): InternalLinkResult? {
  val result = resolveLink(target, url) ?: return null
  @Suppress("REDUNDANT_ELSE_IN_WHEN")
  return when (result) {
    is ResolvedTarget -> InternalLinkResult.Request(result.target.documentationRequest())
    else -> error("Unexpected result: $result") // this fixes Kotlin incremental compilation
  }
}

private fun tryContentUpdater(target: DocumentationTarget, url: String): InternalLinkResult? {
  for (handler in DocumentationLinkHandler.EP_NAME.extensionList) {
    ProgressManager.checkCanceled()
    val updater = handler.contentUpdater(target, url) ?: continue
    return InternalLinkResult.Updater(updater)
  }
  return null
}

internal fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? {
  for (handler in DocumentationLinkHandler.EP_NAME.extensionList) {
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
