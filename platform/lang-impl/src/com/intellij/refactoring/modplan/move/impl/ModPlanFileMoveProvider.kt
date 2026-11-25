// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.move.impl

import com.intellij.model.SymbolPointer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.refactoring.modplan.ModPlanAnalysisResult
import com.intellij.refactoring.modplan.ModPlanConflicts
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.move.ModPlanMovePlaces
import com.intellij.refactoring.modplan.move.ModPlanMoveProblem
import com.intellij.refactoring.modplan.move.ModPlanMoveProvider
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler
import com.intellij.util.concurrency.annotations.RequiresReadLock

/**
 * The provider of the move of files and directories, as the legacy move of files does it. It owns each [PsiFileSystemItem].
 * A provider of a language that is before it in the extension order owns the files of its language.
 * A file that a [MoveFileHandler] of a language processes moves only in the legacy move, see [MoveFileHandler.hasRegisteredHandler].
 */
internal class ModPlanFileMoveProvider : ModPlanMoveProvider {
  override fun isOwner(subject: SymbolPointer): Boolean = subject.element() is PsiFileSystemItem

  /**
   * Whether the subject is not valid, is compiled, is on a read-only file system, or has a file that a handler of a language processes:
   * the legacy move changes such a file, and this provider does not. The check covers each file inside a moved directory, as the legacy
   * move of files asks the handler of each of them.
   */
  @RequiresReadLock
  override fun isLegacyOnly(subject: SymbolPointer): Boolean {
    val item = subject.element() as? PsiFileSystemItem ?: return true
    return !item.isValid || item is PsiCompiledElement || item.virtualFile?.fileSystem?.isReadOnly != false ||
           filesOf(item).any { MoveFileHandler.hasRegisteredHandler(it) }
  }

  /**
   * Gives each subject an item move to the target directory with the same name. For a subject outside the project content, or a content
   * root, the provider refuses the move with [ModPlanMoveProblem.INVALID_SUBJECT].
   */
  @RequiresReadLock
  override fun place(subjects: List<SymbolPointer>, targetDirectory: VirtualFile, request: ModPlanRequest): ModPlanMoveProvider.Placement {
    val fileIndex = ProjectFileIndex.getInstance(request.project())
    val itemMoves = subjects.map { subject ->
      val item = subject.element() as? PsiFileSystemItem
      val file = item?.virtualFile
      if (item == null || file == null || !fileIndex.isInContent(file) || fileIndex.getContentRootForFile(file) == file) {
        return ModPlanMoveProvider.Placement.Refused(ModPlanMoveProblem.INVALID_SUBJECT, item)
      }
      ModPlanMoveProvider.ItemMove(file, targetDirectory, item.name)
    }
    return ModPlanMoveProvider.Placement.Accepted(itemMoves, listOf())
  }

  /** Gives no edit and no conflict: the engine updates the file references, see [ModPlanFileReferenceUpdates]. */
  @RequiresReadLock
  override fun analyze(
    subjects: List<SymbolPointer>,
    targetDirectory: VirtualFile,
    places: ModPlanMovePlaces,
    request: ModPlanRequest,
  ): ModPlanMoveProvider.Analysis =
    ModPlanMoveProvider.Analysis(ModPlanAnalysisResult.Found(listOf(), ModPlanConflicts.EMPTY), listOf(), null)
}

private fun filesOf(item: PsiFileSystemItem): List<PsiFile> {
  if (item is PsiFile) return listOf(item)
  val directory = item.virtualFile ?: return emptyList()
  val psiManager = item.manager
  val files = ArrayList<PsiFile>()
  VfsUtilCore.iterateChildrenRecursively(directory, null) { file ->
    ProgressManager.checkCanceled()
    if (!file.isDirectory) {
      psiManager.findFile(file)?.let { files.add(it) }
    }
    true
  }
  return files
}