// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.move

import com.intellij.model.SymbolPointer
import com.intellij.modcommand.FutureVirtualFile
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.modplan.ModPlan
import com.intellij.refactoring.modplan.ModPlanAnalysisResult
import com.intellij.refactoring.modplan.ModPlanChain
import com.intellij.refactoring.modplan.ModPlanChainBuilder
import com.intellij.refactoring.modplan.ModPlanConflicts
import com.intellij.refactoring.modplan.ModPlanEdit
import com.intellij.refactoring.modplan.ModPlanFinalStep
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.ModPlanRefactoring
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.move.impl.ModPlanFileReferenceUpdates
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil
import com.intellij.util.IncorrectOperationException
import com.intellij.util.PathUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.CollectionFactory
import java.util.function.Supplier

/**
 * The move of files and directories on the ModPlan engine. The subjects are the moved files and directories, and
 * [ModPlanMoveOptions.TARGET_DIRECTORY] holds the URL of the target directory. A language joins through [MoveFileHandler.asModPlan].
 *
 * The flow of the move: [com.intellij.refactoring.modplan.move].
 */
@Service(Service.Level.APP)
class ModPlanMoveRefactoring : ModPlanRefactoring {
  override fun id(): String = REFACTORING_ID

  override fun title(): String = RefactoringBundle.message("move.title")

  /**
   * Every subject is a valid file or directory on a file system that can be written. The handler of each of its files has an adapter,
   * also inside a moved directory. The default check also refuses a binary file, because it edits the content of a subject. A move does
   * not edit the content, so a binary file moves. It searches nothing.
   */
  override fun isSupported(request: ModPlanRequest): Boolean {
    return request.subjects().all { subject ->
      val item = subject.element() as? PsiFileSystemItem
      item != null && item.isValid && item !is PsiCompiledElement && item.virtualFile?.fileSystem?.isReadOnly == false &&
      filesOf(item).all { MoveFileHandler.forElement(it).asModPlan() != null }
    }
  }

  override fun chain(chainBuilder: ModPlanChainBuilder): ModPlanChain<ModPlanFinalStep<ModPlan>> {
    return chainBuilder.finish { planOf(it) }
  }

  private fun planOf(request: ModPlanRequest): ModPlan {
    val requestedItems = request.subjects().map { subject ->
      subject.element() as? PsiFileSystemItem ?: return refusalOf(null, ModPlanMoveProblem.INVALID_SUBJECT)
    }
    val targetDirectoryUrl = request.optionValues().get(ModPlanMoveOptions.TARGET_DIRECTORY)
    val acceptedPlacement = when (val placement = placementOf(request.project(), requestedItems, targetDirectoryUrl)) {
      is Placement.Refused -> return refusalOf(requestedItems.firstOrNull { it.isValid }, placement.problem)
      is Placement.Accepted -> placement
    }
    val items = acceptedPlacement.movedItems
    val target = acceptedPlacement.target

    val adapters = items.flatMap { filesOf(it) }.map { file ->
      file to (MoveFileHandler.forElement(file).asModPlan()
               ?: return ModPlan.RefusedPlan(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, file, null))
    }
    val targetDirectoryPath = target.path
    val places = ModPlanMovePlaces(items.associate { it.virtualFile to childPath(targetDirectoryPath, it.name) })
    val analyses = adapters.map { (file, adapter) ->
      when (val analysis = adapter.analyze(file, places, request)) {
        is ModPlanAnalysisResult.Refused -> return ModPlan.RefusedPlan(analysis.reason, file, null)
        is ModPlanAnalysisResult.Found -> analysis
      }
    }
    val referenceUpdates = ModPlanFileReferenceUpdates.analyze(items, places, request)

    val edits = buildList {
      analyses.flatMapTo(this) { it.edits }
      addAll(referenceUpdates.edits)
      createdDirectoriesOf(target).mapTo(this) { ModPlanEdit.CreateDirectory(it) }
      items.mapTo(this) { ModPlanEdit.Move(it.virtualFile, target, it.name) }
    }
    val conflicts = analyses.fold(referenceUpdates.conflicts) { mergedConflicts, analysis -> mergedConflicts.merge(analysis.conflicts) }
      .merge(ModPlanConflicts.builder().unloadedModules(items).build())
    return try {
      ModPlan.createPlan(edits, conflicts, RefactoringBundle.message("move.title"), REFACTORING_ID, items.first(), false,
                         referenceUpdates.usages, writeEventsOf(items, target))
    }
    catch (e: IncorrectOperationException) {
      ModPlan.RefusedPlan(ModPlanAnalysisResult.RefusedReason.EDIT_FAILED, items.first(), e.message)
    }
  }

  private fun writeEventsOf(items: List<PsiFileSystemItem>, target: VirtualFile): Supplier<ModPlanRefactoring.WriteEvent> {
    val project = items.first().project
    val itemPointers = items.map { SmartPointerManager.createPointer(it) }
    val existingTarget = target.takeUnless { it is FutureVirtualFile }
      ?.let { PsiManager.getInstance(project).findDirectory(it) }
      ?.let { SmartPointerManager.createPointer(it) }
    val fileSystem = existingAncestorOf(target).fileSystem
    val targetDirectoryPath = target.path
    return Supplier {
      val beforeData = RefactoringEventData()
      beforeData.addElements(itemPointers.mapNotNull { it.element })
      existingTarget?.element?.let { beforeData.addTargetElement(it) }
      val changes = itemPointers.mapNotNull { pointer ->
        pointer.element?.let { ModPlanRefactoring.ElementChange(it, ModPlanRefactoring.ElementChange.Kind.MOVED) { pointer.element } }
      }
      ModPlanRefactoring.WriteEvent(beforeData, changes) {
        val afterData = RefactoringEventData()
        val directory = existingTarget?.element
                        ?: fileSystem.findFileByPath(targetDirectoryPath)?.let { PsiManager.getInstance(project).findDirectory(it) }
        directory?.let { afterData.addElement(it) }
        afterData
      }
    }
  }

  companion object {
    private const val REFACTORING_ID = "refactoring.move"

    @JvmStatic
    fun getInstance(): ModPlanMoveRefactoring = service()

    /**
     * @param items              the files and directories to move; at least one
     * @param targetDirectoryUrl the VFS URL of the target directory, see [ModPlanMoveOptions.TARGET_DIRECTORY]
     * @param optionValues       the other options of the move, such as [ModPlanMoveOptions.SEARCH_IN_COMMENTS]
     * @return the request of the move of [items] into the target directory
     */
    @JvmStatic
    @RequiresReadLock
    fun request(
      project: Project,
      items: Collection<PsiFileSystemItem>,
      targetDirectoryUrl: String,
      optionValues: ModPlanOptionValues,
    ): ModPlanRequest {
      return ModPlanRequest(project, items.map { SymbolPointer.of(it) },
                            optionValues.with(ModPlanMoveOptions.TARGET_DIRECTORY, targetDirectoryUrl))
    }

    /**
     * Checks that [items] can go into the target directory, in the order of the constants of [ModPlanMoveProblem]. It searches nothing.
     * An item that repeats another or is inside another moved directory moves with it, so only its validity is checked.
     *
     * @param targetDirectoryUrl the VFS URL of the target directory, see [ModPlanMoveOptions.TARGET_DIRECTORY]
     * @return the first problem; null if the move can go on
     */
    @JvmStatic
    @RequiresReadLock
    fun checkPlacement(project: Project, items: List<PsiFileSystemItem>, targetDirectoryUrl: String): ModPlanMoveProblem? {
      return when (val placement = placementOf(project, items, targetDirectoryUrl)) {
        is Placement.Refused -> placement.problem
        is Placement.Accepted -> null
      }
    }
  }
}

private sealed interface Placement {
  class Refused(val problem: ModPlanMoveProblem) : Placement

  class Accepted(val movedItems: List<PsiFileSystemItem>, val target: VirtualFile) : Placement
}

private fun placementOf(project: Project, items: List<PsiFileSystemItem>, targetDirectoryUrl: String): Placement {
  val fileIndex = ProjectFileIndex.getInstance(project)
  if (!items.all { isMovable(it, fileIndex) }) return Placement.Refused(ModPlanMoveProblem.INVALID_SUBJECT)
  val target = findTargetDirectory(targetDirectoryUrl)
  val nearestDirectory = target?.let { existingAncestorOf(it) }?.takeIf { fileIndex.isInContent(it) }
    ?.let { PsiManager.getInstance(project).findDirectory(it) }
  if (target == null || nearestDirectory == null) return Placement.Refused(ModPlanMoveProblem.INVALID_DESTINATION)
  val movedItems = outermostItems(items)
  if (movedItems.any { isMoveIntoSelf(it, nearestDirectory) }) return Placement.Refused(ModPlanMoveProblem.MOVE_INTO_ITSELF)
  if (movedItems.any { it.virtualFile.parent == target }) return Placement.Refused(ModPlanMoveProblem.ALREADY_IN_DESTINATION)
  val isNameTaken = movedItems.any { target.findChild(it.name) != null }
  val distinctNames = CollectionFactory.createFilePathSet(movedItems.map { it.name }, nearestDirectory.virtualFile.isCaseSensitive)
  if (isNameTaken || distinctNames.size != movedItems.size) return Placement.Refused(ModPlanMoveProblem.TARGET_EXISTS)
  return Placement.Accepted(movedItems, target)
}

private fun findTargetDirectory(targetDirectoryUrl: String): VirtualFile? {
  val fileSystem = VirtualFileManager.extractProtocol(targetDirectoryUrl)?.let { VirtualFileManager.getInstance().getFileSystem(it) }
                   ?: return null
  val path = FileUtil.toCanonicalPath(VirtualFileManager.extractPath(targetDirectoryUrl), '/', true) ?: return null
  return directoryAt(fileSystem, path)
}

private fun directoryAt(fileSystem: VirtualFileSystem, path: String): VirtualFile? {
  fileSystem.findFileByPath(path)?.let { return it }
  val parentPath = PathUtil.getParentPath(path)
  if (parentPath.isEmpty()) return null
  val parent = directoryAt(fileSystem, parentPath) ?: return null
  return FutureVirtualFile(parent, PathUtil.getFileName(path), null)
}

private fun existingAncestorOf(directory: VirtualFile): VirtualFile =
  generateSequence(directory) { it.parent }.first { it !is FutureVirtualFile }

private fun createdDirectoriesOf(directory: VirtualFile): List<FutureVirtualFile> =
  generateSequence(directory as? FutureVirtualFile) { it.parent as? FutureVirtualFile }.toList().asReversed()

private fun isMovable(item: PsiFileSystemItem, fileIndex: ProjectFileIndex): Boolean {
  if (!item.isValid) return false
  val file = item.virtualFile ?: return false
  return fileIndex.isInContent(file) && fileIndex.getContentRootForFile(file) != file
}

private fun isMoveIntoSelf(item: PsiFileSystemItem, targetDirectory: PsiDirectory): Boolean {
  try {
    MoveFilesOrDirectoriesUtil.checkIfMoveIntoSelf(item, targetDirectory)
    return false
  }
  catch (_: IncorrectOperationException) {
    return true
  }
}

private fun outermostItems(items: List<PsiFileSystemItem>): List<PsiFileSystemItem> {
  return PsiTreeUtil.filterAncestors(items.toTypedArray<PsiElement>()).map { it as PsiFileSystemItem }
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

private fun childPath(parentPath: String, name: String): String = if (parentPath.endsWith('/')) parentPath + name else "$parentPath/$name"

private fun refusalOf(item: PsiElement?, problem: ModPlanMoveProblem): ModPlan.RefusedPlan =
  ModPlan.RefusedPlan(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, item, problem.message())