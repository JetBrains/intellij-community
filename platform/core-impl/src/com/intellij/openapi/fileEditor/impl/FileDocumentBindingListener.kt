// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
interface FileDocumentBindingListener {
  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<FileDocumentBindingListener> = Topic(FileDocumentBindingListener::class.java)
  }

  fun fileDocumentBindingChanged(document: Document, oldFile: VirtualFile?, file: VirtualFile?)
}
