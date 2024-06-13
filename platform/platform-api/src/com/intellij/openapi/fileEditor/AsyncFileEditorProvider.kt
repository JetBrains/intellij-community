// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

interface AsyncFileEditorProvider : FileEditorProvider, DumbAware {
  /**
   * This method is intended to be called from background thread. It should perform all time-consuming tasks required to build an editor,
   * and return a builder instance that will be called in EDT to create UI for the editor.
   */
  @RequiresBlockingContext
  @RequiresReadLock
  fun createEditorAsync(project: Project, file: VirtualFile): Builder {
    throw IllegalStateException("Should not be called")
  }

  @Experimental
  @Internal
  suspend fun createFileEditor(
    project: Project,
    file: VirtualFile,
    document: Document?,
    editorCoroutineScope: CoroutineScope,
  ): FileEditor {
    val builder = createEditorBuilder(project = project, file = file, document = document)
    return withContext(Dispatchers.EDT) {
      builder.build()
    }
  }

  @Experimental
  suspend fun createEditorBuilder(project: Project, file: VirtualFile, document: Document?): Builder {
    return readAction { createEditorAsync(project, file) }
  }

  abstract class Builder {
    abstract fun build(): FileEditor
  }
}
