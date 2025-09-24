// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.GotoFileItemProvider
import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.util.gotoByName.GOTO_FILE_SEARCH_IN_NON_INDEXABLE
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.Processor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.contentNonIndexableRoots
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
class NonIndexableFilesSEContributor(event: AnActionEvent) : WeightedSearchEverywhereContributor<Any>, DumbAware, FilesTabSEContributor {
  private val project: Project = event.project!!
  private val navigationHandler: SearchEverywhereNavigationHandler = FileSearchEverywhereNavigationContributionHandler(project)

  override fun getSearchProviderId(): String {
    return javaClass.simpleName
  }

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
    if (!isGotoFileToNonIndexableEnabled(project)) return
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
      .withCaseSensitivity(NameUtil.MatchingCaseSensitivity.NONE)
      .preferringStartMatches()
      .build()

    val workspaceFileIndex = WorkspaceFileIndexEx.getInstance(project)
    val nonIndexableRoots = ReadAction.nonBlocking<Set<VirtualFile>> {
      workspaceFileIndex.contentNonIndexableRoots()
    }.executeSynchronously().map { it.path }


    // search everywhere has limit of entries it allows contibutor to contribute.
    // We want to send good matches first, and only send others later if didn't find enough
    val suboptimalMatches = mutableListOf<VirtualFile>()

    FileBasedIndex.getInstance().iterateNonIndexableFiles(project, null) { file ->
      progressIndicator.checkCanceled()

      val nonIndexableRoot = nonIndexableRoots.firstOrNull { root -> file.path.startsWith(root) } ?: ""
      val pathFromNonIndexableRoot = file.path.removePrefix(nonIndexableRoot).removePrefix("/")

      if (pathMatcher.matches(pathFromNonIndexableRoot)) {
        val matchingDegree = nameMatcher.matchingDegree(file.name)
        if (matchingDegree > 0) {
          val psiItem = PsiManager.getInstance(project).getPsiFileSystemItem(file) ?: return@iterateNonIndexableFiles true
          val itemDescriptor = FoundItemDescriptor<Any>(psiItem, matchingDegree)
          runReadAction { consumer.process(itemDescriptor) }
        }
        else {
          suboptimalMatches.add(file)
          true
        }
      }
      else {
        true
      }
    }

    if (suboptimalMatches.isEmpty() || namePattern.length < 2) return

    val otherNameMatchers = List(namePattern.length - 1) { i ->
      NameUtil.buildMatcher(" " + namePattern.substring(i + 1))
        .withCaseSensitivity(NameUtil.MatchingCaseSensitivity.NONE)
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
          if (!runReadAction { consumer.process(itemDescriptor) }) return
          break
        }
      }
    }
  }


  @ApiStatus.Internal
  class Factory : SearchEverywhereContributorFactory<Any?> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<Any?> {
      return PSIPresentationBgRendererWrapper.wrapIfNecessary(NonIndexableFilesSEContributor(initEvent))
    }

    override fun isAvailable(project: Project?): Boolean {
      return project != null && isGotoFileToNonIndexableEnabled(project)
    }
  }
}

private fun PsiManager.getPsiFileSystemItem(file: VirtualFile) = when {
  file.isDirectory -> runReadAction { findDirectory(file) }
  else -> runReadAction { findFile(file) }
}

private fun isGotoFileToNonIndexableEnabled(project: Project): Boolean =
  Registry.`is`("se.enable.non.indexable.files.contributor") || project.getUserData(GOTO_FILE_SEARCH_IN_NON_INDEXABLE) != false
