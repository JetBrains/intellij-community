// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.ide.util.gotoByName

import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexEx
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
val GOTO_FILE_SEARCH_IN_NON_INDEXABLE: Key<Boolean> = Key.create("search.in.non.indexable")

/**
 * The current implementation loads files into VFS, which we should avoid here.
 * Creating "Disposable virtual files" instead of loading them into VFS should be a better solution.
 * See IJPL-189502 Support non-persistent `VirtualFile`s
 */
@ApiStatus.Internal
class NonIndexableFileNavigationContributor : ChooseByNameContributorEx, DumbAware {

  override fun processNames(processor: Processor<in String>, scope: GlobalSearchScope, filter: IdFilter?) {
    val project = scope.project ?: return
    if (!isGotoFileToNonIndexableEnabled(project)) return
    val filenamesProcessed = hashSetOf<String>()
    FileBasedIndex.getInstance().iterateNonIndexableFiles(project, scope, { file ->
      val filename = file.name
      if (filenamesProcessed.add(filename)) {
        processor.process(filename)
      }
      else {
        true
      }
    })
  }


  override fun processElementsWithName(name: String, processor: Processor<in NavigationItem>, parameters: FindSymbolParameters) {
    if (!isGotoFileToNonIndexableEnabled(parameters.project)) return

    val nonIndexableFilesFilter = nonIndexableFilesFilter(parameters.searchScope, parameters.project)
    val parametersFilter = parameters.idFilter

    // we want to process only non-indexable files, yet want to respect `idFilter` from parameters
    //FIXME RC: we can't really use IdFilter with non-indexable files, because non-indexable files could be 'transient'
    //          (cache-avoiding), so they are not always have fileId/implement VirtualFileWithId
    val idFilter = when {
      parametersFilter == null -> nonIndexableFilesFilter
      else -> idFilter { id -> parametersFilter.containsFileId(id) && nonIndexableFilesFilter.containsFileId(id) }
    }

    //FIXME RC: we do not really search among non-indexable files -- we search among indexable files, but with filter 'non-indexable'
    //          instead we should really use FileBasedIndex.getInstance().iterateNonIndexableFiles(), and filter files by name
    //          (better to do that multithreaded)
    DefaultFileNavigationContributor.processElementsWithName(name, processor, parameters, idFilter)
  }
}

private fun isGotoFileToNonIndexableEnabled(project: Project): Boolean =
  Registry.`is`("search.in.non.indexable") || project.getUserData(GOTO_FILE_SEARCH_IN_NON_INDEXABLE) == true


private inline fun idFilter(crossinline filter: (Int) -> Boolean): IdFilter = object : IdFilter() {
  override fun containsFileId(id: Int): Boolean = filter(id)
}

private fun nonIndexableFilesFilter(scope: GlobalSearchScope, project: Project): IdFilter {
  return when (val projectIdFilter = (FileBasedIndex.getInstance() as FileBasedIndexEx).extractIdFilter(scope, project)) {
    null -> idFilter { true }
    else -> idFilter { id -> !projectIdFilter.containsFileId(id) }
  }
}
