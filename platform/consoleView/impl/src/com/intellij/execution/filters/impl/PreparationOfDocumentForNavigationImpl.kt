// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters.impl

import com.intellij.execution.filters.PreparationOfDocumentForNavigation
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ex.ActionUtil.underModalProgress
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.EDT

internal class PreparationOfDocumentForNavigationImpl : PreparationOfDocumentForNavigation() {
  override fun prepareDocument(
    file: VirtualFile,
    project: Project,
  ): Document? {
    if (Registry.`is`("hyperlink.ide.decompiler.open.file") &&
        EDT.isCurrentThreadEdt() && !ApplicationManager.getApplication().isWriteAccessAllowed()) {
      return underModalProgress(project, IdeBundle.message("progress.title.preparing.navigation"),
                                Computable {
                                  ReadAction.computeCancellable<Document?, RuntimeException?>(ThrowableComputable {
                                    ProjectLocator.withPreferredProject(file, project).use {
                                      // need to load decompiler text
                                      FileDocumentManager.getInstance().getDocument(file)
                                    }
                                  })
                                })
    }
    return super.prepareDocument(file, project)
  }
}
