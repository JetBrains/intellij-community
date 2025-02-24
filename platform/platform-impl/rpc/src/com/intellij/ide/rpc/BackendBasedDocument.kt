// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.rpc

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.extensions.ExtensionPointName
import fleet.util.UID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Allows binding the [Document] created on the frontend side with a backend's document created by [backendDocumentIdProvider].
 * [backendDocumentIdProvider] most likely should be an RPC call which will return [BackendDocumentId].
 * [BackendDocumentId] may be acquired by using [bindToFrontend] method on the backend side.
 *
 * This binding is useful when [Document] is provided by the plugins on the backend side, but you would like to have a local [Document].
 *
 * Please note that this binding happens asynchronously, so you shouldn't expect that it will happen immediately after the method call.
 *
 * @see bindToFrontend
 */
@ApiStatus.Internal
fun Document.bindToBackend(backendDocumentIdProvider: suspend (FrontendDocumentId) -> BackendDocumentId?) {
  val frontendDocument = this@bindToBackend
  service<BackendBasedDocumentCoroutineScopeProvider>().cs.launch(Dispatchers.EDT) {
    val frontendDocumentId = FrontendDocumentId(UID.random())
    val registry = FrontendDocumentIdRegistry.EP_NAME.extensionList.firstOrNull()
    registry?.registerFrontendDocumentId(frontendDocumentId, frontendDocument)

    var backendDocumentId: BackendDocumentId? = null
    try {
      backendDocumentId = backendDocumentIdProvider(frontendDocumentId)
    }
    finally {
      if (backendDocumentId == null) {
        // if backendDocumentId != null, frontend's document host should unregister the document itself
        registry?.unregisterFrontendDocumentId(frontendDocumentId)
      }
    }
  }
}

/**
 * Binds the [Document] created on the backend side with the [Document] created on the frontend where [bindToBackend] was called.
 *
 * Please note that this binding happens asynchronously, so you shouldn't expect that it will happen immediately after the method call.
 */
@ApiStatus.Internal
suspend fun Document.bindToFrontend(frontendDocumentId: FrontendDocumentId): BackendDocumentId {
  val backendDocument = this@bindToFrontend
  val id = BackendDocumentId(UID.random())
  withContext(Dispatchers.EDT) {
    val binder = BackendDocumentBinder.EP_NAME.extensionList.firstOrNull()
    binder?.bindDocument(frontendDocumentId, backendDocument)
  }
  return id
}

@ApiStatus.Internal
@Serializable
data class FrontendDocumentId(val uid: UID)

@ApiStatus.Internal
@Serializable
data class BackendDocumentId(val uid: UID)


@ApiStatus.Internal
interface FrontendDocumentIdRegistry {
  companion object {
    val EP_NAME: ExtensionPointName<FrontendDocumentIdRegistry> =
      ExtensionPointName<FrontendDocumentIdRegistry>("com.intellij.frontendDocumentIdRegistry")
  }

  // TODO: should we deal with session here,
  //  so that it will be registered for session not not app level?
  fun registerFrontendDocumentId(frontendDocumentId: FrontendDocumentId, frontendDocument: Document)

  fun unregisterFrontendDocumentId(frontendDocumentId: FrontendDocumentId)
}

@ApiStatus.Internal
interface BackendDocumentBinder {
  companion object {
    val EP_NAME: ExtensionPointName<BackendDocumentBinder> =
      ExtensionPointName<BackendDocumentBinder>("com.intellij.backendDocumentBinder")
  }

  fun bindDocument(frontendDocumentId: FrontendDocumentId, baseDocument: Document)
}


// TODO: should BackendBasedDocument take cs as argument? Let's see by usages
@Service(Service.Level.APP)
private class BackendBasedDocumentCoroutineScopeProvider(val cs: CoroutineScope)