// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.GotoFileItemProvider
import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.actions.searcheverywhere.footer.createPsiExtendedInfo
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FilesDeque
import com.intellij.util.text.matching.MatchingMode
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.ListCellRenderer

/**
 * Marker interface, indicating that this contributor will be added to the "Files" tab in Search Everywhere
 */
@ApiStatus.Internal
interface FilesTabSEContributor {
  companion object {
    @ApiStatus.Internal
    @JvmStatic
    fun SearchEverywhereContributor<*>.isFilesTabContributor(): Boolean {
      return this is FilesTabSEContributor || this is SearchEverywhereContributorWrapper && this.getEffectiveContributor().isFilesTabContributor()
    }

    @ApiStatus.Internal
    @JvmStatic
    fun SearchEverywhereContributor<*>.isMainFilesContributor(): Boolean {
      return this is FileSearchEverywhereContributor || this is SearchEverywhereContributorWrapper && this.getEffectiveContributor().isMainFilesContributor()
    }
  }
}

private val LOG = Logger.getInstance(NonIndexableFilesSEContributor::class.java)

@ApiStatus.Internal
class NonIndexableFilesSEContributor(event: AnActionEvent) : WeightedSearchEverywhereContributor<Any>,
                                                             DumbAware,
                                                             FilesTabSEContributor,
                                                             SearchEverywhereExtendedInfoProvider {
  private val project: Project = event.project!!
  private val navigationHandler: SearchEverywhereNavigationHandler = FileSearchEverywhereNavigationContributionHandler(project)

  override fun getSearchProviderId(): String = ID

  override fun getGroupName(): @Nls String {
    return IdeBundle.message("search.everywhere.group.name.non.indexable.files")
  }

  override fun getSortWeight(): Int {
    return 1000
  }

  override fun showInFindResults(): Boolean = true

  override fun isShownInSeparateTab(): Boolean = false

  override fun processSelectedItem(selected: Any, modifiers: Int, searchText: String): Boolean {
    if (selected !is PsiElement) return false

    navigationHandler.gotoSelectedItem(selected, modifiers, searchText)
    return true
  }

  override fun getElementsRenderer(): ListCellRenderer<in Any> {
    return SearchEverywherePsiRenderer(this)
  }

  override fun isDumbAware(): Boolean {
    return true
  }

  /**
   * @implNote instead of running under one large read action, launches many short blocking RAs
   */
  override fun fetchWeightedElements(pattern: String, progressIndicator: ProgressIndicator, consumer: Processor<in FoundItemDescriptor<Any>>) {
    if (!isGotoFileToNonIndexableEnabled()) return
    if (pattern.isEmpty()) return

    val (namePattern, pathPattern) = run {
      val sanitizedPattern = GotoFileItemProvider.getSanitizedPattern(pattern, null).removePrefix("*")
      sanitizedPattern.substringAfterLast('/').removePrefix("*") to sanitizedPattern
    }

    /**
     * Checks if the file path matches the pattern. If it does, the file will be submitted
     */
    val pathMatcher = GotoFileItemProvider.getQualifiedNameMatcher(pathPattern)

    val nameMatcher = NameUtil.buildMatcher("*" + namePattern)
      .withMatchingMode(MatchingMode.IGNORE_CASE)
      .preferringStartMatches()
      .build()

    // search everywhere has limit of entries it allows contibutor to contribute.
    // We want to send good matches first, and only send others later if didn't find enough
    val suboptimalMatches = mutableListOf<VirtualFile>()

    fun processFile(file: VirtualFile): Boolean {
      val workspaceFileIndex = WorkspaceFileIndexEx.getInstance(project)
      val nonIndexableRoot = runReadAction { workspaceFileIndex.findNonIndexableFileSet(file) }?.root
      // path includes root
      val pathFromNonIndexableRoot = file.path.removePrefix(nonIndexableRoot?.parent?.path ?: "").removePrefix("/")

      if (!pathMatcher.matches(pathFromNonIndexableRoot)) {
        return true // file doesn't match pattern, skip
      }

      val matchingDegree = nameMatcher.matchingDegree(file.name)
      if (matchingDegree <= 0) {
        suboptimalMatches.add(file)
        return true // suboptimal match, process later, after "optimal" matches
      }

      val psiItem = PsiManager.getInstance(project).getPsiFileSystemItem(file) ?: return true
      val itemDescriptor = FoundItemDescriptor<Any>(psiItem, matchingDegree)
      return ReadAction.nonBlocking<Boolean> { consumer.process(itemDescriptor) }.executeSynchronously()
    }

    val useBfs = Registry.`is`("se.enable.non.indexable.files.use.bfs")
    val useBfsUnderOneReadAction = !Registry.`is`("se.enable.non.indexable.files.use.bfs.blocking.read.actions")
    if (useBfs && useBfsUnderOneReadAction) {
      // BFS under one big cancellable read action
      val filesDeque = ReadAction.nonBlocking<FilesDeque> {
        FilesDeque.nonIndexableDequeue(project)
      }.executeSynchronously()
      ReadAction.nonBlocking<Unit> {
        while (true) {
          progressIndicator.checkCanceled()
          val file = filesDeque.computeNext()
          if (file == null || !processFile(file)) break
        }
      }.executeSynchronously()
    }
    else if (useBfs) {
      // BFS with many small blocking read actions
      val filesDeque = ReadAction.nonBlocking<FilesDeque> {
        FilesDeque.nonIndexableDequeue(project)
      }.executeSynchronously()
      while (true) {
        progressIndicator.checkCanceled()
        val file = filesDeque.computeNext()
        if (file == null || !processFile(file)) break
      }
    }
    else {
      // DFS with many small blocking read actions
      FileBasedIndex.getInstance().iterateNonIndexableFiles(project, null) { file ->
        progressIndicator.checkCanceled()
        processFile(file = file)
      }
    }


    if (suboptimalMatches.isEmpty() || namePattern.length < 2) return

    val otherNameMatchers = List(namePattern.length - 1) { i ->
      NameUtil.buildMatcher(" " + namePattern.substring(i + 1))
        .withMatchingMode(MatchingMode.IGNORE_CASE)
        .build()
    }

    for (file in suboptimalMatches) {
      // binary search instead of linear?
      for (i in otherNameMatchers.indices) {
        val matcher = otherNameMatchers[i]
        val matchingDegree = matcher.matchingDegree(file.name)
        if (matchingDegree > 0) {
          val psiItem = PsiManager.getInstance(project).getPsiFileSystemItem(file) ?: continue
          val weight = matchingDegree * (otherNameMatchers.size - i) / (otherNameMatchers.size + 1)
          val itemDescriptor = FoundItemDescriptor<Any>(psiItem, weight)
          if (!ReadAction.nonBlocking<Boolean> {
              consumer.process(itemDescriptor)
            }.executeSynchronously()) return
          break
        }
      }
    }
  }

  override fun createExtendedInfo(): @Nls ExtendedInfo = createPsiExtendedInfo()

  companion object {
    @ApiStatus.Internal
    const val ID: String = "NonIndexableFilesSEContributor"
  }

  @ApiStatus.Internal
  class Factory : SearchEverywhereContributorFactory<Any?> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<Any?> {
      return PSIPresentationBgRendererWrapper.wrapIfNecessary(NonIndexableFilesSEContributor(initEvent))
    }

    override fun isAvailable(project: Project?): Boolean {
      return project != null && isGotoFileToNonIndexableEnabled()
    }
  }
}

private fun WorkspaceFileIndexEx.findNonIndexableFileSet(
  file: VirtualFile,
): WorkspaceFileSet? = findFileSet(file, true, false, true, false, false, false)

private fun PsiManager.getPsiFileSystemItem(file: VirtualFile): PsiFileSystemItem? = when {
  file.isDirectory -> ReadAction.nonBlocking<PsiDirectory?> { findDirectory(file) }.executeSynchronously()
  else -> ReadAction.nonBlocking<PsiFile?> { findFile(file) }.executeSynchronously()
}

private fun isGotoFileToNonIndexableEnabled(): Boolean = Registry.`is`("se.enable.non.indexable.files.contributor")
