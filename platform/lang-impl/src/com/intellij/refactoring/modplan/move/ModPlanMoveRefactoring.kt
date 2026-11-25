// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.move

import com.intellij.model.SymbolPointer
import com.intellij.modcommand.FutureVirtualFile
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
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
import com.intellij.refactoring.modplan.move.ModPlanMoveProvider.ItemMove
import com.intellij.refactoring.modplan.move.impl.ModPlanFileReferenceUpdates
import com.intellij.util.IncorrectOperationException
import com.intellij.util.PathUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.CollectionFactory
import java.util.function.Supplier

/**
 * The move on the ModPlan engine. The subjects are the moved elements, such as files and directories, and
 * [ModPlanMoveOptions.TARGET_DIRECTORY] holds the URL of the target directory. A language joins through [ModPlanMoveProvider].
 *
 * The flow of the move: [com.intellij.refactoring.modplan.move].
 */
@Service(Service.Level.APP)
class ModPlanMoveRefactoring : ModPlanRefactoring {
  override fun id(): String = REFACTORING_ID

  override fun title(): String = RefactoringBundle.message("move.title")

  /** Every subject is valid, and it has an owner that is not legacy only for it, see [ModPlanMoveProvider.isOwner]. It searches nothing. */
  override fun isSupported(request: ModPlanRequest): Boolean {
    return request.subjects().all { subject -> subject.element()?.isValid == true && ownerOf(subject) != null }
  }

  override fun chain(chainBuilder: ModPlanChainBuilder): ModPlanChain<ModPlanFinalStep<ModPlan>> {
    return chainBuilder.finish { planOf(it) }
  }

  private fun planOf(request: ModPlanRequest): ModPlan {
    val resolved = when (val resolution = resolve(request)) {
      is Resolution.Refused -> return refusalOf(resolution.place, resolution.problem)
      is Resolution.NotSupported -> return ModPlan.RefusedPlan(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED, resolution.place, null)
      is Resolution.Resolved -> resolution
    }
    val project = request.project()
    val target = resolved.target
    val itemMoves = resolved.itemMoves
    val places = ModPlanMovePlaces(itemMoves.associate { it.item to childPath(it.targetDirectory.path, it.newName) })

    val analyses = ArrayList<ModPlanAnalysisResult.Found>()
    val usages = ArrayList<SmartPsiFileRange>()
    for ([owner, ownerSubjects] in resolved.ownedSubjects) {
      val analysis = owner.analyze(ownerSubjects, target, places, request)
      when (val result = analysis.result) {
        is ModPlanAnalysisResult.Refused ->
          return ModPlan.RefusedPlan(result.reason, analysis.place ?: ownerSubjects.first().element(), null)
        is ModPlanAnalysisResult.Found -> analyses.add(result)
      }
      usages.addAll(analysis.usages)
    }
    val items = itemMoves.mapNotNull { psiItemOf(project, it.item) }
    val referenceUpdates = ModPlanFileReferenceUpdates.analyze(items, places, request)
    usages.addAll(referenceUpdates.usages)

    val contentEdits = ArrayList<ModPlanEdit>()
    val creations = ArrayList<ModPlanEdit.Create>()
    val deletions = ArrayList<ModPlanEdit.Delete>()
    for (edit in analyses.flatMap { it.edits }) {
      when (edit) {
        is ModPlanEdit.Update, is ModPlanEdit.UpdateRange, is ModPlanEdit.ReplaceText -> contentEdits.add(edit)
        is ModPlanEdit.Create -> creations.add(edit)
        is ModPlanEdit.Delete -> deletions.add(edit)
        is ModPlanEdit.Move, is ModPlanEdit.CreateDirectory ->
          throw IllegalArgumentException("A move provider gives no edit of the kind ${edit.javaClass.simpleName}: $edit")
      }
    }
    contentEdits.addAll(referenceUpdates.edits)

    val createdDirectories = LinkedHashMap<String, FutureVirtualFile>()
    for (directory in listOf(target) + itemMoves.map { it.targetDirectory }) {
      for (created in createdDirectoriesOf(directory)) {
        createdDirectories.putIfAbsent(created.path, created)
      }
    }
    val edits = buildList {
      addAll(contentEdits)
      createdDirectories.values.mapTo(this) { ModPlanEdit.CreateDirectory(it) }
      addAll(creations)
      itemMoves.mapTo(this) { move ->
        val directory = (move.targetDirectory as? FutureVirtualFile)?.let { createdDirectories[it.path] } ?: move.targetDirectory
        ModPlanEdit.Move(move.item, directory, move.newName)
      }
      addAll(deletions)
      resolved.emptiedDirectories.mapTo(this) { ModPlanEdit.Delete(it) }
    }
    val conflicts = analyses.fold(referenceUpdates.conflicts) { mergedConflicts, analysis -> mergedConflicts.merge(analysis.conflicts) }
      .merge(ModPlanConflicts.builder().unloadedModules(resolved.subjects).build())
    val anchor = resolved.subjects.first()
    return try {
      ModPlan.createPlan(edits, conflicts, RefactoringBundle.message("move.title"), REFACTORING_ID, anchor, false,
                         usages, writeEventsOf(resolved.subjects, target))
    }
    catch (e: IncorrectOperationException) {
      ModPlan.RefusedPlan(ModPlanAnalysisResult.RefusedReason.EDIT_FAILED, anchor, e.message)
    }
  }

  private fun writeEventsOf(items: List<PsiElement>, target: VirtualFile): Supplier<ModPlanRefactoring.WriteEvent> {
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
     * @param items              the elements to move, such as files and directories; at least one
     * @param targetDirectoryUrl the VFS URL of the target directory, see [ModPlanMoveOptions.TARGET_DIRECTORY]
     * @param optionValues       the other options of the move, such as [ModPlanMoveOptions.SEARCH_IN_COMMENTS]
     * @return the request of the move of [items] into the target directory
     */
    @JvmStatic
    @RequiresReadLock
    fun request(
      project: Project,
      items: Collection<PsiElement>,
      targetDirectoryUrl: String,
      optionValues: ModPlanOptionValues,
    ): ModPlanRequest {
      return ModPlanRequest(project, items.map { SymbolPointer.of(it) },
                            optionValues.with(ModPlanMoveOptions.TARGET_DIRECTORY, targetDirectoryUrl))
    }

    /**
     * Checks that [items] can go into the target directory. It searches nothing.
     *
     * The engine checks the subjects, then the target directory, then [ModPlanMoveProvider.place] of each owner, then the merged item
     * moves of all owners. The last checks give the problems of [ModPlanMoveProblem] in the order of its constants. A subject that repeats
     * another or is inside another moved subject moves with it, so the engine checks only its owner.
     *
     * @param targetDirectoryUrl the VFS URL of the target directory, see [ModPlanMoveOptions.TARGET_DIRECTORY]
     * @return the first problem; null if the move can go on
     */
    @JvmStatic
    @RequiresReadLock
    fun checkPlacement(project: Project, items: List<PsiElement>, targetDirectoryUrl: String): ModPlanMoveProblem? {
      if (!items.all { it.isValid }) return ModPlanMoveProblem.INVALID_SUBJECT
      return when (val resolution = resolve(request(project, items, targetDirectoryUrl, ModPlanOptionValues.EMPTY))) {
        is Resolution.Refused -> resolution.problem
        is Resolution.NotSupported -> ModPlanMoveProblem.INVALID_SUBJECT
        is Resolution.Resolved -> null
      }
    }

    /** @return the first provider that owns [subject]; null if there is none or if it is legacy only for [subject] */
    internal fun ownerOf(subject: SymbolPointer): ModPlanMoveProvider? {
      return ModPlanMoveProvider.EP_NAME.findFirstSafe { it.isOwner(subject) }?.takeUnless { it.isLegacyOnly(subject) }
    }
  }
}

private sealed interface Resolution {
  class Refused(val problem: ModPlanMoveProblem, val place: PsiElement?) : Resolution

  class NotSupported(val place: PsiElement?) : Resolution

  class Resolved(
    val subjects: List<PsiElement>,
    val ownedSubjects: Map<ModPlanMoveProvider, List<SymbolPointer>>,
    val target: VirtualFile,
    val itemMoves: List<ItemMove>,
    val emptiedDirectories: List<VirtualFile>,
  ) : Resolution
}

private fun resolve(request: ModPlanRequest): Resolution {
  val pointers = request.subjects()
  val elements = ArrayList<PsiElement>()
  val owners = ArrayList<ModPlanMoveProvider>()
  val indices = HashMap<PsiElement, Int>()
  for (pointer in pointers) {
    val element = pointer.element()?.takeIf { it.isValid } ?: return Resolution.Refused(ModPlanMoveProblem.INVALID_SUBJECT, null)
    owners.add(ModPlanMoveRefactoring.ownerOf(pointer) ?: return Resolution.NotSupported(element))
    indices.putIfAbsent(element, elements.size)
    elements.add(element)
  }
  if (elements.isEmpty()) return Resolution.Refused(ModPlanMoveProblem.INVALID_SUBJECT, null)

  val fileIndex = ProjectFileIndex.getInstance(request.project())
  val target = findTargetDirectory(request.optionValues().get(ModPlanMoveOptions.TARGET_DIRECTORY))
  val nearestDirectory = target?.let { existingAncestorOf(it) }?.takeIf { it.isDirectory && fileIndex.isInContent(it) }
  if (target == null || nearestDirectory == null) return Resolution.Refused(ModPlanMoveProblem.INVALID_DESTINATION, elements.first())

  val subjects = PsiTreeUtil.filterAncestors(elements.toTypedArray())
  val ownedSubjects = LinkedHashMap<ModPlanMoveProvider, ArrayList<SymbolPointer>>()
  for (subject in subjects) {
    val index = indices.getValue(subject)
    ownedSubjects.getOrPut(owners[index]) { ArrayList() }.add(pointers[index])
  }
  val itemMoves = ArrayList<ItemMove>()
  val emptiedDirectories = ArrayList<VirtualFile>()
  for ([owner, ownerSubjects] in ownedSubjects) {
    when (val placement = owner.place(ownerSubjects, target, request)) {
      is ModPlanMoveProvider.Placement.Refused -> return Resolution.Refused(placement.problem, placement.place ?: elements.first())
      is ModPlanMoveProvider.Placement.Accepted -> {
        itemMoves.addAll(placement.itemMoves)
        emptiedDirectories.addAll(placement.emptiedDirectories)
      }
    }
  }
  val problem = problemOf(itemMoves, nearestDirectory.isCaseSensitive)
  if (problem != null) return Resolution.Refused(problem, elements.first())
  return Resolution.Resolved(subjects.toList(), ownedSubjects, target, itemMoves, emptiedDirectories.distinct())
}

private fun problemOf(itemMoves: List<ItemMove>, isCaseSensitive: Boolean): ModPlanMoveProblem? {
  if (itemMoves.any { isMoveIntoSelf(it) }) return ModPlanMoveProblem.MOVE_INTO_ITSELF
  if (itemMoves.any { it.item.parent == it.targetDirectory && it.item.name == it.newName }) return ModPlanMoveProblem.ALREADY_IN_DESTINATION
  val isNameTaken = itemMoves.any { it.targetDirectory.findChild(it.newName) != null }
  val distinctPaths = CollectionFactory.createFilePathSet(itemMoves.map { childPath(it.targetDirectory.path, it.newName) }, isCaseSensitive)
  if (isNameTaken || distinctPaths.size != itemMoves.size) return ModPlanMoveProblem.TARGET_EXISTS
  return null
}

private fun isMoveIntoSelf(itemMove: ItemMove): Boolean =
  itemMove.item.isDirectory && VfsUtilCore.isAncestor(itemMove.item, existingAncestorOf(itemMove.targetDirectory), false)

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

private fun psiItemOf(project: Project, file: VirtualFile): PsiFileSystemItem? {
  val psiManager = PsiManager.getInstance(project)
  return if (file.isDirectory) psiManager.findDirectory(file) else psiManager.findFile(file)
}

private fun childPath(parentPath: String, name: String): String = if (parentPath.endsWith('/')) parentPath + name else "$parentPath/$name"

private fun refusalOf(item: PsiElement?, problem: ModPlanMoveProblem): ModPlan.RefusedPlan =
  ModPlan.RefusedPlan(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE, item, problem.message())