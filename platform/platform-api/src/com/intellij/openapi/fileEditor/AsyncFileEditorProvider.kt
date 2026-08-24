// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.OverrideOnly

interface AsyncFileEditorProvider : FileEditorProvider, DumbAware {
  /**
   * This method is intended to be called from a background thread. It should perform all time-consuming tasks required to build an editor
   * and return a builder instance that will be called in EDT to create UI for the editor.
   */
  @RequiresReadLock
  @OverrideOnly
  fun createEditorAsync(project: Project, file: VirtualFile): Builder {
    throw IllegalStateException("Should not be called")
  }

  /**
   * Creates the editor, possibly across several suspension points.
   *
   * This method can be cancelled at any suspension point - the file may be closed while its composite is still loading. Because
   * `withContext`, `async` and `coroutineScope` discard their result on cancellation, an editor created here can be lost before it
   * ever reaches the composite, and then nothing releases it. An implementation that creates an editor (or obtains one from another
   * provider) and then suspends must therefore hand it over with [registerCreatedFileEditor] right where it is created; the platform
   * releases whatever the cancelled open left behind. Note that the editor this method *returns* is tracked by the caller, so only
   * the intermediate ones need registering.
   */
  @Experimental
  suspend fun createFileEditor(
    project: Project,
    file: VirtualFile,
    document: Document?,
    editorCoroutineScope: CoroutineScope,
  ): FileEditor {
    val builder = readAction { createEditorAsync(project = project, file = file) }
    return withContext(Dispatchers.EDT) {
      builder.build()
    }
  }

  abstract class Builder {
    abstract fun build(): FileEditor
  }
}
