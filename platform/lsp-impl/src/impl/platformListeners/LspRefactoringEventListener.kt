// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.platformListeners

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.impl.features.workspaceFileOperations.LspWillRenameFilesHandler
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener

/** The ID of the refactoring in [com.intellij.refactoring.rename.RenameProcessor]. */
internal const val RENAME_REFACTORING_ID = "refactoring.rename"

/** The ID of the refactoring in [com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor]. */
internal const val MOVE_REFACTORING_ID = "refactoring.move"

internal class LspRefactoringEventListener(private val project: Project) : RefactoringEventListener {
  override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {
    if (beforeData == null) return
    if (refactoringId == RENAME_REFACTORING_ID || refactoringId == MOVE_REFACTORING_ID) {
      LspWillRenameFilesHandler.renameOrMoveRefactoringStarted(project, refactoringId, beforeData)
    }
  }

  override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {}
}
