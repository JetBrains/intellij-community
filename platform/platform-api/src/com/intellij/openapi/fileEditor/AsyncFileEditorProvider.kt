/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.fileEditor

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.OverrideOnly

interface AsyncFileEditorProvider : FileEditorProvider {
  /**
   * This method is intended to be called from background thread. It should perform all time-consuming tasks required to build an editor,
   * and return a builder instance that will be called in EDT to create UI for the editor.
   */
  @OverrideOnly
  @RequiresBlockingContext
  @RequiresReadLock
  fun createEditorAsync(project: Project, file: VirtualFile): Builder

  @Experimental
  @OverrideOnly
  suspend fun createEditorBuilder(project: Project, file: VirtualFile): Builder {
    return readAction { createEditorAsync(project, file) }
  }

  abstract class Builder {
    @OverrideOnly
    abstract fun build(): FileEditor
  }
}
