// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFileSystemItem
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import org.jetbrains.annotations.ApiStatus

/**
 * Forbids a move of a file or a directory into a directory.
 *
 * A [MoveHandlerDelegate] cannot do that: the first delegate that accepts a move wins, and a dumb project asks no delegate.
 * [MoveFilesOrDirectoriesUtil.checkMove] asks each extension, so the move fails with the text of [getProblem].
 * [MoveHandler.canMove] asks them for a target that is known before the move starts, so a paste and a drop are not offered.
 */
@ApiStatus.Internal
interface FileMoveVeto {
  /**
   * The text that tells the user why [item] cannot move into [targetDirectory], or `null` when this extension allows the move.
   * The call can come from any thread, with or without a read action.
   */
  fun getProblem(item: PsiFileSystemItem, targetDirectory: PsiDirectory): @NlsContexts.DialogMessage String?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<FileMoveVeto> = ExtensionPointName("com.intellij.refactoring.fileMoveVeto")

    /** The problem of the first extension that forbids the move of [item] into [targetDirectory], or `null` when no extension does. */
    @JvmStatic
    fun findProblem(item: PsiFileSystemItem, targetDirectory: PsiDirectory): @NlsContexts.DialogMessage String? {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.getProblem(item, targetDirectory) }
    }
  }
}
