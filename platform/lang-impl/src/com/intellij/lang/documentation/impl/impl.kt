// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems") // KTIJ-19938

package com.intellij.lang.documentation.impl

import com.intellij.lang.documentation.*
import com.intellij.model.Pointer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.util.AsyncSupplier
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
    tryResolveLink(targetPointer, url)
    ?: tryContentUpdater(targetPointer, url)
    ?: InternalLinkResult.CannotResolve
  }
}

private suspend fun tryResolveLink(targetPointer: Pointer<out DocumentationTarget>, url: String): InternalLinkResult? {
  return when (val resolveResult = resolveLink(targetPointer::dereference, url)) {
    InternalResolveLinkResult.InvalidTarget -> InternalLinkResult.InvalidTarget
    InternalResolveLinkResult.CannotResolve -> null
    is InternalResolveLinkResult.Value -> InternalLinkResult.Request(resolveResult.value)
  }
}

internal sealed class InternalResolveLinkResult<out X> {
  object InvalidTarget : InternalResolveLinkResult<Nothing>()
  object CannotResolve : InternalResolveLinkResult<Nothing>()
  class Value<X>(val value: X) : InternalResolveLinkResult<X>()
}

internal suspend fun resolveLink(
  targetSupplier: () -> DocumentationTarget?,
  url: String,
): InternalResolveLinkResult<DocumentationRequest> {
  return resolveLink(targetSupplier, url, DocumentationTarget::documentationRequest)
}

/**
 * @param ram read action mapper - a function which would be applied to resolved [DocumentationTarget] while holding the read action
 */
internal suspend fun <X> resolveLink(
  targetSupplier: () -> DocumentationTarget?,
  url: String,
  ram: (DocumentationTarget) -> X,
): InternalResolveLinkResult<X> {
  val readActionResult = readAction {
    resolveLinkInReadAction(targetSupplier, url, ram)
  }
  return when (readActionResult) {
    is ResolveLinkInReadActionResult.Sync -> readActionResult.syncResult
    is ResolveLinkInReadActionResult.Async -> asyncTarget(readActionResult.supplier, ram)
  }
}

private suspend fun <X> asyncTarget(
  supplier: AsyncSupplier<LinkResolveResult.Async?>,
  ram: (DocumentationTarget) -> X,
): InternalResolveLinkResult<X> {
  val asyncLinkResolveResult: LinkResolveResult.Async? = supplier.invoke()
  if (asyncLinkResolveResult == null) {
    return InternalResolveLinkResult.CannotResolve
  }
  @Suppress("REDUNDANT_ELSE_IN_WHEN")
  when (asyncLinkResolveResult) {
    is AsyncResolvedTarget -> {
      val pointer = asyncLinkResolveResult.pointer
      return readAction {
        val target: DocumentationTarget? = pointer.dereference()
        if (target == null) {
          InternalResolveLinkResult.InvalidTarget
        }
        else {
          InternalResolveLinkResult.Value(ram(target))
        }
      }
    }
    else -> error("Unexpected result: $asyncLinkResolveResult")
  }
}

private sealed class ResolveLinkInReadActionResult<out X> {
  class Sync<X>(val syncResult: InternalResolveLinkResult<X>) : ResolveLinkInReadActionResult<X>()
  class Async(val supplier: AsyncSupplier<LinkResolveResult.Async?>) : ResolveLinkInReadActionResult<Nothing>()
}

private fun <X> resolveLinkInReadAction(
  targetSupplier: () -> DocumentationTarget?,
  url: String,
  m: (DocumentationTarget) -> X,
): ResolveLinkInReadActionResult<X> {
  val documentationTarget = targetSupplier()
                            ?: return ResolveLinkInReadActionResult.Sync(InternalResolveLinkResult.InvalidTarget)
  @Suppress("REDUNDANT_ELSE_IN_WHEN")
  return when (val linkResolveResult: LinkResolveResult? = resolveLink(documentationTarget, url)) {
    null -> ResolveLinkInReadActionResult.Sync(InternalResolveLinkResult.CannotResolve)
    is ResolvedTarget -> ResolveLinkInReadActionResult.Sync(InternalResolveLinkResult.Value(m(linkResolveResult.target)))
    is AsyncLinkResolveResult -> ResolveLinkInReadActionResult.Async(linkResolveResult.supplier)
    else -> error("Unexpected result: $linkResolveResult") // this fixes Kotlin incremental compilation
  }
}

private fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? {
  for (handler in DocumentationLinkHandler.EP_NAME.extensionList) {
    ProgressManager.checkCanceled()
    return handler.resolveLink(target, url) ?: continue
  }
  return null
}

private suspend fun tryContentUpdater(targetPointer: Pointer<out DocumentationTarget>, url: String): InternalLinkResult? {
  return readAction {
    contentUpdaterInReadAction(targetPointer, url)
  }
}

private fun contentUpdaterInReadAction(targetPointer: Pointer<out DocumentationTarget>, url: String): InternalLinkResult? {
  val target = targetPointer.dereference()
               ?: return InternalLinkResult.InvalidTarget
  val updater = contentUpdater(target, url)
                ?: return null
  return InternalLinkResult.Updater(updater)
}

private fun contentUpdater(target: DocumentationTarget, url: String): ContentUpdater? {
  for (handler in DocumentationLinkHandler.EP_NAME.extensionList) {
    ProgressManager.checkCanceled()
    return handler.contentUpdater(target, url) ?: continue
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
