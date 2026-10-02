// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class PreparationOfDocumentForNavigation {
  /**
   * Returns the document prepared for navigation (e.g. by invoking decompilers)
   */
  open fun prepareDocument(file: VirtualFile, project: Project): Document? {
    return ProjectLocator.withPreferredProject(file, project).use {
      /* need to load decompiler text */
      FileDocumentManager.getInstance().getDocument(file)
    }
  }
}