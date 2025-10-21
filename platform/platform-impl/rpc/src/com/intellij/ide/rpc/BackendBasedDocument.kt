// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.rpc

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import fleet.util.UID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Customizes how [Document] and its [Editor]s will be bind to the backend.
 *
 * This binding is useful when [Document] is provided by the plugins on the backend side,
 * but you would like to have a local [Document] and its [Editor]s.
 *
 * Please note that this binding happens asynchronously, so you shouldn't expect that it will happen immediately after the method call.
 *
 * @see BackendDocumentBindBuilder
 * @see bindToFrontend
 */
@ApiStatus.Internal
fun Document.bindToBackend(
  builder: BackendDocumentBindBuilder.() -> Unit,
) {
  service<BackendBasedDocumentCoroutineScopeProvider>().cs.launch(Dispatchers.EDT) {
    val builder = BackendDocumentBindBuilder().apply(builder)
    val backendDocumentIdProvider = builder.backendDocumentIdProvider
    if (backendDocumentIdProvider != null) {
      bindToBackend(backendDocumentIdProvider, builder.onBindingDispose)
    }

    if (builder.bindEditors) {
      // mark the document, so future editors will be bind
      bindEditorsToBackend()
      // bind current editors (since they might be created during backends' documents initialization)
      bindCurrentEditors()
    }
  }
}

/**
 * Binds the [Document] created on the backend side with the [Document] created on the frontend where [bindToBackend] was called.
 *
 * Optional [project] parameter is a context project for the document. For example, if a document comes from an editor,
 * it should be `editor.project`. If the document has some language support, you should also pass the project it is bound to.
 *
 * Please note that this binding happens asynchronously, so you shouldn't expect that it will happen immediately after the method call.
 */
@ApiStatus.Internal
suspend fun Document.bindToFrontend(frontendDocumentId: FrontendDocumentId, project: Project? = null): BackendDocumentId {
  val backendDocument = this@bindToFrontend
  val id = BackendDocumentId(UID.random())
  withContext(Dispatchers.EDT) {
    val binder = BackendDocumentBinder.EP_NAME.extensionList.firstOrNull()
    binder?.bindDocument(frontendDocumentId, backendDocument, project)
  }
  return id
}

private fun Document.bindCurrentEditors() {
  val binder = FrontendEditorBinder.EP_NAME.extensionList.firstOrNull()
  if (binder != null) {
    for (editor in EditorFactory.getInstance().editors(this)) {
      binder.bindEditor(editor)
    }
  }
}

/**
 * This API provides a bridge between RD protocol synchronization and IntelliJ Platform.
 * Implementation details of the binding process:
 *   1. We create [FrontendDocumentId] and store given [Document] to the storage by this [FrontendDocumentId].
 *   2. We request backend to create an [Document], where [bindToFrontend] should be called.
 *   3. [bindToFrontend] starts RD synchronization through [BackendDocumentHost].
 *   4. Frontend handles this synchronization and substitute stored [Document] there through [FrontendDocumentHost].
 */
private suspend fun Document.bindToBackend(
  backendDocumentIdProvider: suspend (FrontendDocumentId) -> BackendDocumentId?,
  onBindingDispose: (() -> Unit)?,
) {
  val frontendDocument = this
  val frontendDocumentId = FrontendDocumentId(UID.random())
  val registry = FrontendDocumentIdRegistry.EP_NAME.extensionList.firstOrNull()
  registry?.registerFrontendDocumentId(frontendDocumentId, frontendDocument, onBindingDispose)
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

@ApiStatus.Internal
class BackendDocumentBindBuilder {
  /**
   * Allows binding the [Document] created on the frontend side with a backend's document.
   *
   * [backendDocumentIdProvider] most likely should be an RPC call which will return [BackendDocumentId].
   * [BackendDocumentId] may be acquired by using [bindToFrontend] method on the backend side.
   *
   * @see bindToFrontend
   */
  var backendDocumentIdProvider: (suspend (FrontendDocumentId) -> BackendDocumentId?)? = null

  /**
   * Allows binding all the [Editor]s created for the given [Document] with the backend.
   *
   * It is useful when you would like to enable backend's features of the [Editor]
   * which is created by the platform using given [Document] (e.g. [EditorTextField]).
   */
  var bindEditors: Boolean = false

  /**
   * This callback will be called when the binding is disposed.
   */
  var onBindingDispose: (() -> Unit)? = null
}

@ApiStatus.Internal
@ConsistentCopyVisibility
@Serializable
data class FrontendDocumentId internal constructor(val uid: UID) {
  companion object {
    @ApiStatus.Internal
      /**
       * Should be used only for deserialization!
       */
    fun fromString(encoded: String): FrontendDocumentId {
      return FrontendDocumentId(UID.fromString(encoded))
    }
  }
}

@ApiStatus.Internal
@ConsistentCopyVisibility
@Serializable
data class BackendDocumentId internal constructor(val uid: UID)


@ApiStatus.Internal
interface FrontendDocumentIdRegistry {
  companion object {
    val EP_NAME: ExtensionPointName<FrontendDocumentIdRegistry> =
      ExtensionPointName<FrontendDocumentIdRegistry>("com.intellij.frontendDocumentIdRegistry")
  }

  // TODO: should we deal with session here,
  //  so that it will be registered for session not not app level?
  fun registerFrontendDocumentId(frontendDocumentId: FrontendDocumentId, frontendDocument: Document, onBindingDispose: (() -> Unit)?)

  fun unregisterFrontendDocumentId(frontendDocumentId: FrontendDocumentId)
}

@ApiStatus.Internal
interface FrontendEditorBinder {
  companion object {
    val EP_NAME: ExtensionPointName<FrontendEditorBinder> =
      ExtensionPointName<FrontendEditorBinder>("com.intellij.frontendEditorBinder")
  }

  fun bindEditor(editor: Editor)
}

@ApiStatus.Internal
interface BackendDocumentBinder {
  companion object {
    val EP_NAME: ExtensionPointName<BackendDocumentBinder> =
      ExtensionPointName<BackendDocumentBinder>("com.intellij.backendDocumentBinder")
  }

  fun bindDocument(frontendDocumentId: FrontendDocumentId, baseDocument: Document, project: Project?)
}


// TODO: should BackendBasedDocument take cs as argument? Let's see by usages
@Service(Service.Level.APP)
private class BackendBasedDocumentCoroutineScopeProvider(val cs: CoroutineScope)