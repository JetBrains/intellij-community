// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.DirectorySearchEngine
import com.intellij.find.FindModel
import com.intellij.find.FindModelExtension
import com.intellij.find.impl.CandidateFilter.Source
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopeUtil
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.impl.VirtualFileEnumeration
import com.intellij.util.indexing.ConcurrentFileTraversal
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexEx
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods
import com.intellij.util.indexing.roots.kind.ContentOrigin

private val LOG = logger<ScopeWalker>()

/**
 * Walks the scope of a "Find in Path" search: phase 3 of [FindSearchRun], the brute-force search.
 * [roots] builds the [WalkPlan] on the producer coroutine; [expand] unfolds one walk item of that plan ([WorkItem.Walk]) on a
 * worker, under its read action, into more walk items and candidate files.
 * The walker holds only the facts of the model; everything a search decides at its start lives in its [WalkPlan].
 */
internal class ScopeWalker(
  private val findModel: FindModel,
  private val project: Project,
  private val candidateFilter: CandidateFilter,
  private val moduleToSearchIn: Module?,
  private val directoryToSearchIn: VirtualFile?,
  private val withSubdirectories: Boolean,
  /** True when [directoryToSearchIn] stands for the whole project, as in JetBrains Light, where every file is non-indexable. */
  private val directoryStandsForProject: Boolean,
  private val customScope: GlobalSearchScope?,
) {
  /**
   * Receives what [expand] finds. Both methods return false when the search stopped; [isStopped] tells it before a lookup.
   * The contract is synchronous: everything an item finds reaches the sink before [expand] returns, because the item
   * finishes then and its phase may drain. So a [DirectorySearchEngine] or an iterator must call its callback before
   * it returns, never later from another thread.
   */
  interface Sink {
    /** True when the search stopped. */
    val isStopped: Boolean

    fun addItem(item: WorkItem.Walk): Boolean

    fun addCandidate(file: VirtualFile, source: Source): Boolean
  }

  /**
   * What [roots] decided for one search; [expand] reads it on the workers.
   *
   * @param items        the root walk items
   * @param searchInLibs true when the custom scope searches in libraries, so a library iterator is walked too
   * @param engines      the [DirectorySearchEngine]s whose [DirectorySearchEngine.canSearch] accepted the model
   */
  data class WalkPlan(
    val items: List<WorkItem.Walk>,
    val searchInLibs: Boolean,
    val engines: List<DirectorySearchEngine>,
    /**
     * False when a project search skips the walk of the non-indexable files: the registry key is off, or a reliable
     * searcher reported [com.intellij.find.FindInProjectSearchEngine.Coverage.ALL_CANDIDATES], also for the directory
     * that stands for the project; true in every other branch.
     * Only a walked plan earns [com.intellij.find.FindInProjectSearchEngine.ScanObserver.nonIndexedScanCompleted].
     */
    val nonIndexableWalked: Boolean,
  )

  /**
   * Builds the walk items from _one of_ {custom scope | directory | module | indexable providers}, plus the
   * [FindModelExtension]s. Runs on the producer coroutine, once per search and only when the walk phase runs; asks
   * [DirectorySearchEngine.canSearch] here, outside a read action.
   *
   * @param skipNonIndexableRoots true when a reliable searcher reported
   *                              [com.intellij.find.FindInProjectSearchEngine.Coverage.ALL_CANDIDATES]
   *                              (see [SearcherSet.nonIndexableCovered]), so the walk of the non-indexable files is unnecessary
   */
  suspend fun roots(skipNonIndexableRoots: Boolean): WalkPlan {
    val globalCustomScope = customScope
    val searchInLibs = globalCustomScope != null && readAction { globalCustomScope.isSearchInLibraries }
    val engines = DirectorySearchEngine.EP_NAME.extensionList.filter { it.canSearch(findModel) }

    val modelCustomScope = if (findModel.isCustomScope) findModel.customScope else null
    val items = ArrayList<WorkItem.Walk>()
    var nonIndexableWalked = true

    if (modelCustomScope is LocalSearchScope) {
      GlobalSearchScopeUtil.getLocalScopeFiles(modelCustomScope).mapTo(items) { WorkItem.Walk.File(it) }
    }
    else if (modelCustomScope is VirtualFileEnumeration) {
      // GlobalSearchScope can include files out of project roots e.g., FileScope / FilesScope. The starting files are all
      // in VFS already (because they have ids), but file-tree down from them could be not in VFS cache yet -- so it is worth
      // wrapping all the files into cache-avoiding wrappers here, and avoid trashing VFS cache with new entries during lookup:
      for (file in FileBasedIndexEx.toFileIterable(modelCustomScope.asArray())) {
        items.add(WorkItem.Walk.File(NewVirtualFile.asCacheAvoiding(file)))
      }
    }
    else if (directoryToSearchIn != null) {
      // The directory can lie outside the project. Wrap a persistent VFS directory so the walk does not fill the VFS cache
      // with entries from a rarely used file tree. An LSP virtual file is not persistent and is walked as is.
      val directory = (directoryToSearchIn as? NewVirtualFile)?.asCacheAvoiding() ?: directoryToSearchIn
      if (withSubdirectories) {
        // the directory that stands for the project holds only non-indexable files, so a reliable searcher covers its walk too
        if (directoryStandsForProject && skipNonIndexableRoots) {
          nonIndexableWalked = false
        }
        else {
          items.add(WorkItem.Walk.File(directory))
        }

        // DirectorySearchEngine is not obliged to add unsaved documents to the search queue - do it now.
        getUnsavedDocumentsUnderDirectory(directoryToSearchIn).mapTo(items) { WorkItem.Walk.File(it) }
      }
      else {
        directory.children.mapTo(items) { WorkItem.Walk.File(it) }
      }
      //MAYBE RC: should we return early here? Should FindModelExtension be added if user explicitly
      //          request a search in a specific directory _only_?
    }
    else if (moduleToSearchIn != null) {
      LOG.assertTrue(!IdeProductMode.isLight, "Search in module should not happen in ijLight. Searched module: $moduleToSearchIn")

      val storage = WorkspaceModel.getInstance(project).currentSnapshot
      val moduleEntity = checkNotNull(storage.resolve(ModuleId(moduleToSearchIn.name)))
      //MAYBE RC: wrap files into a cache-avoiding wrappers?
      //          It seems useless, since files are all indexable, so they are already scanned and cached in VFS -- but is it true?
      IndexableEntityProviderMethods.createIterators(moduleEntity, storage, project).mapTo(items) { WorkItem.Walk.Indexable(it) }
    }
    else if (FileBasedIndex.getInstance() !is FileBasedIndexEx) {
      // A host can implement FileBasedIndex without FileBasedIndexEx. Then walk the workspace model modules.
      val storage = WorkspaceModel.getInstance(project).currentSnapshot
      for (moduleEntity in storage.entities(ModuleEntity::class.java)) {
        IndexableEntityProviderMethods.createIterators(moduleEntity, storage, project).mapTo(items) { WorkItem.Walk.Indexable(it) }
      }
      nonIndexableWalked = false
    }
    else {
      LOG.assertTrue(!IdeProductMode.isLight, "Search in project should not happen in ijLight. Please use search in directory instead.")

      val indexes = FileBasedIndex.getInstance() as FileBasedIndexEx
      //Don't wrap those files in cache-avoiding wrappers: indexable files are scanned, and hence (will be) cached in VFS anyway:
      indexes.getIndexableFilesProviders(project).mapTo(items) { WorkItem.Walk.Indexable(it) }

      if (Registry.`is`("find.in.files.in.non.indexable.enable") && !skipNonIndexableRoots) {
        val searchInLibraries = when (modelCustomScope) {
          null -> false // default scope is 'Project', no libraries there
          is GlobalSearchScope -> modelCustomScope.isSearchInLibraries
          else -> true
        }

        //MAYBE RC: currently nonIndexableFiles() returns transient files already -- but maybe it is safer to return _regular_ files
        //          from nonIndexableFiles(), and wrap them all into transient here, in a unified way?
        val traversalRoots = readAction { ConcurrentFileTraversal.nonIndexableTraversal(project, searchInLibraries).roots }
        traversalRoots.mapTo(items) { WorkItem.Walk.Traversal(it) }
      }
      else {
        nonIndexableWalked = false
      }
    }

    FindModelExtension.EP_NAME.extensionList.mapTo(items) { WorkItem.Walk.Extension(it) }
    return WalkPlan(items, searchInLibs, engines, nonIndexableWalked)
  }

  /**
   * Unfolds one walk item of the plan into more walk items and candidate files. Runs on a worker, under its read action.
   * A directory item claims its directory ([CandidateFilter.claimDirectory]) and offers its children: a plain file as a
   * candidate, a directory as a walk item; it stops offering when the search stopped. A restarted run keeps the claim, lists
   * the directory again and offers the children again: [CandidateFilter.admitOnProducer] skips the files offered before, and
   * [CandidateFilter.claimDirectory] the directories.
   * A traversal item of a directory offers its children the same way ([offerTraversalChild]), without a claim: a restarted run
   * offers its directory children again as new walk items, and [CandidateFilter.admitOnProducer] skips the files they offer twice.
   */
  fun expand(plan: WalkPlan, item: WorkItem.Walk, sink: Sink) {
    ProgressManager.checkCanceled()

    when (item) {
      is WorkItem.Walk.Indexable -> {
        val iterator = item.iterator
        if (plan.searchInLibs || iterator.origin is ContentOrigin) {
          iterator.iterateFiles(project, { file -> file.isDirectory || sink.addCandidate(file, Source.WALK) }, VirtualFileFilter.ALL)
        }
      }
      is WorkItem.Walk.Traversal -> {
        val traversalItem = item.traversalItem
        var stopped = false
        val shouldProcessFile = traversalItem.expand { children ->
          if (!stopped) {
            for (child in children) {
              if (!offerTraversalChild(child, sink)) {
                stopped = true
                break
              }
            }
          }
        }
        if (shouldProcessFile && !traversalItem.file.isDirectory) {
          sink.addCandidate(traversalItem.file, Source.WALK)
        }
      }
      is WorkItem.Walk.Extension -> {
        //the extension files pass the mask and are searched once, but skip the exclusion and scope checks (Rider's external scope):
        //MAYBE RC: withSubdirs is ignored here: all the directories are just skipped.
        item.extension.iterateAdditionalFiles(findModel, project) { file -> sink.addCandidate(file, Source.EXTENSION) }
      }
      is WorkItem.Walk.File -> {
        val file = item.file
        if (file.isDirectory) expandDirectory(plan, item, file, sink) else sink.addCandidate(file, Source.WALK)
      }
    }
  }

  private fun expandDirectory(plan: WalkPlan, item: WorkItem.Walk.File, directory: VirtualFile, sink: Sink) {
    if (!candidateFilter.claimDirectory(item, directory)) return
    if (directoryToSearchIn != null && withSubdirectories) {
      // note that search engine may unfold more than one level and eventually visit already indexed, excluded or ignored
      // files or directories. This might be a performance problem, but does not affect correctness: all the files
      // will be checked against the WSM and requested scope later when this search task deals with individual files.
      // MAYBE-ANK: While it is searcher's responsibility to work at least faster than the default implementation if it
      // claims positive weight, it is also a good idea to not pass directories with excludes and indexed files into them.
      val directorySearchEngine = DirectorySearchEngine.selectDirectorySearchEngine(directory, plan.engines)
      if (directorySearchEngine != null) {
        // an engine cannot be told to stop, so the batches after a stop are skipped here
        var stopped = false
        directorySearchEngine.searchDirectory(directory, findModel) { files ->
          if (!stopped) {
            for (file in files) {
              if (!offerChild(file, sink)) {
                stopped = true
                break
              }
            }
          }
        }
      }
      else {
        LOG.error("At least DefaultDirectorySearchEngine must be available. No directory search engine for $directory")
      }
    }
  }

  /**
   * A plain file becomes a candidate right here, on the worker that listed it; only a directory becomes a walk item.
   * So a file is one queued item, as a searcher file is.
   *
   * @return false when the search stopped
   */
  private fun offerChild(file: VirtualFile, sink: Sink): Boolean {
    return if (file.isDirectory) sink.addItem(WorkItem.Walk.File(file)) else sink.addCandidate(file, Source.WALK)
  }

  /**
   * The [offerChild] of a non-indexable traversal: a plain file is classified right here, by one WorkspaceFileIndex lookup
   * ([ConcurrentFileTraversal.TraversalItem.getSubtreeProcessingMode]), and becomes a candidate when the traversal processes it;
   * only a directory becomes a walk item. A file is not expanded: that would list its children, one failed listing per file.
   *
   * @return false when the search stopped
   */
  private fun offerTraversalChild(child: ConcurrentFileTraversal.TraversalItem, sink: Sink): Boolean {
    if (child.file.isDirectory) return sink.addItem(WorkItem.Walk.Traversal(child))
    // a large directory makes many lookups in one read action; this is the cancel point that the item of each file had
    ProgressManager.checkCanceled()
    // a skipped file offers nothing, so the sink cannot refuse it: check the stop before its lookup
    if (sink.isStopped) return false
    return !child.getSubtreeProcessingMode().shouldProcessRoot || sink.addCandidate(child.file, Source.WALK)
  }
}

private fun getUnsavedDocumentsUnderDirectory(directory: VirtualFile): Collection<VirtualFile> {
  val cacheableDirectory = if (directory is CacheAvoidingVirtualFile) directory.asCacheable() ?: return emptyList() else directory

  val fileDocumentManager = FileDocumentManager.getInstance()
  return fileDocumentManager.unsavedDocuments.mapNotNull { document ->
    fileDocumentManager.getFile(document)?.takeIf { VfsUtilCore.isAncestor(cacheableDirectory, it, false) }
  }
}
