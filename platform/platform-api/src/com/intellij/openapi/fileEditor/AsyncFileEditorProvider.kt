// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.Experimental

interface AsyncFileEditorProvider : FileEditorProvider {
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
  suspend fun createEditorBuilder(project: Project, file: VirtualFile): Builder {
    return readAction { createEditorAsync(project, file) }
  }

  abstract class Builder {
    abstract fun build(): FileEditor
  }
}
