// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.move.impl

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.PsiReference
import com.intellij.psi.SmartPsiFileRange
import com.intellij.psi.createSmartFileRange
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.modplan.ModPlanConflicts
import com.intellij.refactoring.modplan.ModPlanEdit
import com.intellij.refactoring.modplan.ModPlanRefactoring
import com.intellij.refactoring.modplan.ModPlanRequest
import com.intellij.refactoring.modplan.move.ModPlanMoveOptions
import com.intellij.refactoring.modplan.move.ModPlanMovePlaces
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil.MovedFileOrDirectoryUsageInfo
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.util.IncorrectOperationException
import com.intellij.util.PathUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.Nls

/**
 * The updates of the file references for a move on the ModPlan engine. They cover the references to the moved items and the references
 * in the moved files. Each update is an edit of the copies, see [com.intellij.refactoring.modplan.move.ModPlanMoveUsage].
 */
internal object ModPlanFileReferenceUpdates {
  /**
   * The result of [analyze].
   *
   * @property edits     the edits that give the file references their new paths
   * @property conflicts the file references that the move cannot update
   * @property usages    the places of the updated file references, which a preview shows
   */
  class Result(val edits: List<ModPlanEdit>, val conflicts: ModPlanConflicts, val usages: List<SmartPsiFileRange>)

  /**
   * The updates of the file references that the move of [subjects] changes. They cover the references to each moved item, also inside a
   * moved directory, and the references in each moved file. A reference between the items that move together keeps its path. A
   * reference from a root in a moved file keeps its path if the root stays. A reference at a place that [request] excludes keeps its path.
   * A reference that the move cannot update is a conflict. With [ModPlanMoveOptions.SEARCH_FOR_REFERENCES] off, it searches nothing, and
   * the result is empty.
   *
   * @param subjects the moved files and directories; none is inside another
   */
  @RequiresReadLock
  fun analyze(subjects: List<PsiFileSystemItem>, places: ModPlanMovePlaces, request: ModPlanRequest): Result {
    if (!request.optionValues().get(ModPlanMoveOptions.SEARCH_FOR_REFERENCES)) {
      return Result(listOf(), ModPlanConflicts.EMPTY, listOf())
    }
    val movedItems = subjects.flatMap { itemsOf(it) }
    val referenceUsages = ArrayList<ReferenceUsage>()
    val conflicts = ModPlanConflicts.builder()
    for (item in movedItems) {
      addUsagesOf(item, places, referenceUsages)
    }
    for (file in movedItems.filterIsInstance<PsiFile>()) {
      addUsagesIn(file, places, referenceUsages, conflicts)
    }
    val edits = ArrayList<ModPlanEdit>()
    val usages = ArrayList<SmartPsiFileRange>()
    for (referenceUsage in referenceUsages) {
      val place = placeOf(referenceUsage.usage.updatedReference)
      if (place != null && ModPlanRefactoring.isExcluded(request, place)) continue
      val edit = referenceUsage.usage.asModPlanEdit(places)
      if (edit != null) {
        edits.add(edit)
        place?.let { usages.add(it) }
      }
      else {
        conflicts.broken(referenceUsage.usage.element, referenceUsage.conflictMessage())
      }
    }
    return Result(edits, conflicts.build(), usages)
  }

  /**
   * @return the edit that gives [reference] the path of [target] relative to the directory of its file, both at their places after the
   * move; null if the edit cannot write [reference], such as a reference that is not relative to the directory of its file
   *
   * ```
   * <!-- d/page.html -->
   * <img src="../d/a.png">
   * // move d/page.html and d/a.png into e
   * <img src="a.png">
   * ```
   */
  @JvmStatic
  @RequiresReadLock
  fun updateOf(reference: PsiReference, target: PsiElement, places: ModPlanMovePlaces): ModPlanEdit? {
    val fileReference = reference as? FileReference ?: return null
    val set = fileReference.fileReferenceSet
    val element = set.element
    if (!isDirectoryRelative(set) || ElementManipulators.getManipulator(element) == null) return null
    val file = fileOf(set) ?: return null
    val targetFile = PsiUtilCore.getVirtualFile(target) ?: return null
    val directoryPath = futureDirectoryPathOf(file, places) ?: return null
    val newPath = FileUtil.getRelativePath(directoryPath, futurePathOf(targetFile, places), '/') ?: return null
    val place = placeOf(fileReference) ?: return null
    val elementClass = element.javaClass
    return ModPlanEdit.UpdateRange(place) { copy, range ->
      val copyElement = elementAt(copy, range, elementClass) ?: throw IncorrectOperationException("No $elementClass at $range in $copy")
      ElementManipulators.handleContentChange(copyElement, range.shiftLeft(copyElement.textRange.startOffset), newPath)
    }
  }

  private class ReferenceUsage(val usage: MovedFileOrDirectoryUsageInfo, val targetMoves: Boolean) {
    fun conflictMessage(): @Nls String {
      val text = emphasizedTextOf(usage.updatedReference)
      return if (targetMoves) {
        RefactoringBundle.message("move.modplan.conflict.reference.not.updated", text)
      }
      else {
        RefactoringBundle.message("move.modplan.conflict.moved.reference.not.updated", text)
      }
    }
  }

  private fun addUsagesOf(item: PsiFileSystemItem, places: ModPlanMovePlaces, usages: MutableList<ReferenceUsage>) {
    for (reference in ReferencesSearch.search(item, GlobalSearchScope.projectScope(item.project)).findAll()) {
      if (reference is FileReference) {
        val set = reference.fileReferenceSet
        if (reference !== lastNamedReferenceOf(set)) continue
        if (isDirectoryRelative(set) && keepsRelativePath(reference, item, places)) continue
      }
      usages.add(ReferenceUsage(MovedFileOrDirectoryUsageInfo(reference, item), true))
    }
  }

  private fun addUsagesIn(file: PsiFile,
                          places: ModPlanMovePlaces,
                          usages: MutableList<ReferenceUsage>,
                          conflicts: ModPlanConflicts.Builder) {
    val virtualFile = file.virtualFile
    if (virtualFile == null || file is PsiCompiledElement || file.fileType.isBinary) return
    for (set in fileReferenceSetsOf(file)) {
      val reference = lastNamedReferenceOf(set) ?: continue
      val target = reference.multiResolve(false).firstNotNullOfOrNull { it.element as? PsiFileSystemItem } ?: continue
      val targetFile = target.virtualFile
      if (targetFile == null || places.futurePathOf(targetFile) != null) continue
      if (isDirectoryRelative(set)) {
        if (!keepsRelativePath(reference, target, places)) {
          usages.add(ReferenceUsage(MovedFileOrDirectoryUsageInfo(reference, target), false))
        }
      }
      else if (!keepsRoot(set, virtualFile, places)) {
        conflicts.broken(set.element, RefactoringBundle.message("move.modplan.conflict.moved.reference.not.updated",
                                                                emphasizedTextOf(reference)))
      }
    }
  }

  private fun itemsOf(item: PsiFileSystemItem): List<PsiFileSystemItem> {
    if (item !is PsiDirectory) return listOf(item)
    val psiManager = item.manager
    val items = ArrayList<PsiFileSystemItem>()
    VfsUtilCore.iterateChildrenRecursively(item.virtualFile, null) { file ->
      ProgressManager.checkCanceled()
      val child = if (file.isDirectory) psiManager.findDirectory(file) else psiManager.findFile(file)
      child?.let { items.add(it) }
      true
    }
    return items
  }

  private fun fileReferenceSetsOf(file: PsiFile): Collection<FileReferenceSet> {
    val sets = LinkedHashSet<FileReferenceSet>()
    file.accept(object : PsiRecursiveElementWalkingVisitor() {
      override fun visitElement(element: PsiElement) {
        ProgressManager.checkCanceled()
        for (reference in element.references) {
          if (reference is FileReference) {
            sets.add(reference.fileReferenceSet)
          }
        }
        super.visitElement(element)
      }
    })
    return sets
  }

  private fun placeOf(reference: PsiReference): SmartPsiFileRange? {
    if (reference !is FileReference) return null
    val range = TextRange(reference.fileReferenceSet.startInElement, reference.rangeInElement.endOffset)
    return reference.fileReferenceSet.element.createSmartFileRange(range)
  }

  private fun elementAt(file: PsiFile, range: TextRange, elementClass: Class<out PsiElement>): PsiElement? {
    var element = file.findElementAt(range.startOffset)
    while (element != null && element !is PsiFile) {
      if (elementClass.isInstance(element) && element.textRange.contains(range)) return element
      element = element.parent
    }
    return null
  }

  private fun lastNamedReferenceOf(set: FileReferenceSet): FileReference? = set.allReferences.lastOrNull { it.text.isNotEmpty() }

  private fun isDirectoryRelative(set: FileReferenceSet): Boolean {
    if (set.isAbsolutePathReference) return false
    val directory = fileOf(set)?.parent ?: return false
    return set.defaultContexts.singleOrNull()?.virtualFile == directory
  }

  private fun keepsRelativePath(reference: FileReference, target: PsiFileSystemItem, places: ModPlanMovePlaces): Boolean {
    val file = fileOf(reference.fileReferenceSet) ?: return false
    val targetFile = target.virtualFile ?: return false
    val futureDirectoryPath = futureDirectoryPathOf(file, places) ?: return false
    val path = reference.fileReferenceSet.allReferences.take(reference.index + 1).joinToString("/") { it.decode(it.text) }
    val futureResolvedPath = FileUtil.toCanonicalPath("$futureDirectoryPath/$path", '/', true) ?: return false
    return FileUtil.pathsEqual(futureResolvedPath, futurePathOf(targetFile, places))
  }

  private fun keepsRoot(set: FileReferenceSet, file: VirtualFile, places: ModPlanMovePlaces): Boolean {
    val roots = set.defaultContexts.mapNotNull { it.virtualFile }
    val root = roots.filter { VfsUtilCore.isAncestor(it, file, true) }.maxByOrNull { it.path.length } ?: return false
    val futurePath = futurePathOf(file, places)
    val futureRoot = roots.filter { FileUtil.isAncestor(it.path, futurePath, true) }.maxByOrNull { it.path.length }
    return futureRoot == root && places.futurePathOf(root) == null
  }

  private fun fileOf(set: FileReferenceSet): VirtualFile? {
    val element = set.element
    return InjectedLanguageManager.getInstance(element.project).getTopLevelFile(element)?.originalFile?.virtualFile
  }

  private fun futurePathOf(file: VirtualFile, places: ModPlanMovePlaces): String = places.futurePathOf(file) ?: file.path

  private fun futureDirectoryPathOf(file: VirtualFile, places: ModPlanMovePlaces): String? {
    return places.futurePathOf(file)?.let { PathUtil.getParentPath(it) } ?: file.parent?.path
  }

  private fun emphasizedTextOf(reference: PsiReference): String {
    val text = (reference as? FileReference)?.fileReferenceSet?.pathString ?: reference.rangeInElement.substring(reference.element.text)
    return CommonRefactoringUtil.htmlEmphasize(text)
  }
}