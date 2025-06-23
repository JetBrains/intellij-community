// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexEx
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import org.jetbrains.annotations.ApiStatus

/**
 * The current implementation loads files into VFS, which we should avoid here.
 * Creating "Disposable virtual files" instead of loading them into VFS should be a better solution.
 * See IJPL-189502 Support non-persistent `VirtualFile`s
 */
@ApiStatus.Internal
class NonIndexableFileNavigationContributor : ChooseByNameContributorEx, DumbAware {

  private inline fun processFileTree(
    roots: Set<VirtualFile>,
    scope: GlobalSearchScope,
    workspaceFileIndex: WorkspaceFileIndexEx,
    crossinline processor: (VirtualFile) -> Boolean,
  ) {
    // to avoid processing subtrees multiple times, we process roots first and then do not enter them again
    roots.forEach { root -> if (!processor(root)) return }
    val rootsChildren = roots.asSequence().flatMap { it.children?.asSequence() ?: emptySequence() }
    rootsChildren.forEach { root ->
      val res = VfsUtil.visitChildrenRecursively(root, object : VirtualFileVisitor<Any?>() {
        override fun visitFileEx(file: VirtualFile): Result = when {
          file in roots -> SKIP_CHILDREN
          !scope.contains(file) -> SKIP_CHILDREN
          file.isIndexedOrExcluded(workspaceFileIndex) -> SKIP_CHILDREN
          !processor(file) -> skipTo(root) // terminate processing
          else -> CONTINUE
        }
      })
      if (res.skipChildren && res.skipToParent == root) return
    }
  }

  override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
    if (!Registry.`is`("search.in.non.indexable")) return

    val project = scope.project ?: return
    val workspaceFileIndex = WorkspaceFileIndex.getInstance(project) as WorkspaceFileIndexEx

    val roots = workspaceFileIndex.contentUnindexedRoots()
    processFileTree(roots, scope, workspaceFileIndex) { file -> processor.process(file.name) }
  }


  override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
    if (!Registry.`is`("search.in.non.indexable")) return

    val nonIndexableFilesFilter = nonIndexableFilesFilter(parameters.searchScope, parameters.project)
    val parametersFilter = parameters.idFilter

    // we want to process only non-indexable files, yet want to respect `idFilter` from parameters
    val idFilter = when {
      parametersFilter == null -> nonIndexableFilesFilter
      else -> idFilter { id -> parametersFilter.containsFileId(id) && nonIndexableFilesFilter.containsFileId(id) }
    }

    DefaultFileNavigationContributor.processElementsWithName(name, processor, parameters, idFilter)
  }
}


private inline fun idFilter(crossinline filter: (Int) -> Boolean): IdFilter = object : IdFilter() {
  override fun containsFileId(id: Int): Boolean = filter(id)
}

private fun nonIndexableFilesFilter(scope: GlobalSearchScope, project: Project): IdFilter {
  return when (val projectIdFilter = (FileBasedIndex.getInstance() as FileBasedIndexEx).extractIdFilter(scope, project)) {
    null -> idFilter { true }
    else -> idFilter { id -> !projectIdFilter.containsFileId(id) }
  }
}

private fun VirtualFile.isIndexedOrExcluded(workspaceFileIndex: WorkspaceFileIndexEx): Boolean {
  return workspaceFileIndex.isIndexable(this) || !workspaceFileIndex.isInWorkspace(this)
}

@ApiStatus.Internal
fun WorkspaceFileIndexEx.contentUnindexedRoots(): Set<VirtualFile> {
  val roots = mutableSetOf<VirtualFile>()
  visitFileSets { fileSet, _ ->
    val root = fileSet.root
    if (fileSet.kind == WorkspaceFileKind.CONTENT_NON_INDEXABLE && !isIndexable(root)) {
      roots.add(fileSet.root)
    }
  }
  return roots
}
