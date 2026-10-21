// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

/**
 * Receives an event when [FileDocumentManagerBase] binds a [Document] to a [VirtualFile] or unbinds it.
 *
 * The manager publishes the event in these cases:
 * - It loads a new document for a file. `oldFile` is `null`.
 * - [FileDocumentManagerBase.registerDocument] binds a document to a non-physical file. `oldFile` is the previous file or `null`.
 * - [FileDocumentManagerBase.unbindFileFromDocument] removes the binding. `file` is `null`.
 *
 * The manager publishes no event when the garbage collector removes a weakly cached document.
 * The manager publishes the event on the thread that changed the binding, after it releases its lock.
 */
@ApiStatus.Internal
interface FileDocumentBindingListener {
  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<FileDocumentBindingListener> = Topic(FileDocumentBindingListener::class.java)
  }

  /**
   * Is called after the binding of [document] changes from [oldFile] to [file].
   *
   * @param oldFile the file that was bound to [document] before the change, or `null` if no file was bound.
   * @param file the file that is bound to [document] after the change, or `null` if the change removed the binding.
   */
  fun fileDocumentBindingChanged(document: Document, oldFile: VirtualFile?, file: VirtualFile?)
}
