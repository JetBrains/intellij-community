// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Collects the file editors created while a composite is being opened, so that the platform can release the ones abandoned
 * by a cancelled open.
 *
 * [AsyncFileEditorProvider.createFileEditor] implementations create editors across suspension points, and `withContext`,
 * `async` and `coroutineScope` all discard their result once the coroutine is cancelled - which is what happens when the file
 * is closed while its composite is still loading. Such an editor never reaches the composite, so `EditorComposite.dispose`
 * cannot release it and the backing editor stays alive until the project is closed.
 *
 * A provider is not expected to look this element up directly - see [registerCreatedFileEditor].
 */
@ApiStatus.Experimental
class CreatedFileEditorSink : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<CreatedFileEditorSink>

  private val editors = ConcurrentLinkedQueue<FileEditor>()

  /**
   * Takes responsibility for [editor] until the composite adopts it. Registering the same editor more than once is harmless.
   */
  fun register(editor: FileEditor) {
    editors.add(editor)
  }

  @ApiStatus.Internal
  fun toList(): List<FileEditor> = editors.toList()
}

/**
 * Hands [editor] over to the [CreatedFileEditorSink] of the current context, if any, so that it is released should the open
 * that created it be cancelled. Providers that create an editor inside a non-suspending block (typically
 * `withContext(Dispatchers.EDT) { writeIntentReadAction { ... } }`) should read the sink with [createdFileEditorSink] before
 * entering the block and call [CreatedFileEditorSink.register] from within it - registering after the block has returned is
 * already too late, because that is the boundary at which the result gets discarded.
 *
 * A no-op outside a composite open (e.g. under the blocking [FileEditorProvider.createEditor]), where nothing tracks editors.
 */
@ApiStatus.Experimental
suspend fun registerCreatedFileEditor(editor: FileEditor) {
  createdFileEditorSink()?.register(editor)
}

/**
 * Returns the [CreatedFileEditorSink] of the current context, to be used from a non-suspending block. See [registerCreatedFileEditor].
 */
@ApiStatus.Experimental
suspend fun createdFileEditorSink(): CreatedFileEditorSink? = currentCoroutineContext()[CreatedFileEditorSink]
