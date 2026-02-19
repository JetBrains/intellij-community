// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.GotoActionBase
import com.intellij.ide.actions.GotoFileItemProvider
import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.actions.searcheverywhere.footer.createPsiExtendedInfo
import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.util.Processor
import com.intellij.util.indexing.FilesDeque
import com.intellij.util.text.matching.MatchingMode
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.ListCellRenderer
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Marker interface, indicating that this contributor will be added to the "Files" tab in Search Everywhere
 */
@ApiStatus.Internal
interface FilesTabSEContributor {
  val psiContext: SmartPsiElementPointer<PsiElement?>?
  val project: Project

  fun setScope(scope: ScopeDescriptor)
  fun setHiddenTypes(hiddenTypes: List<FileTypeRef>)

  companion object {
    @ApiStatus.Internal
    @JvmStatic
    fun SearchEverywhereContributor<*>.isFilesTabContributor(): Boolean = unwrapFilesTabContributorIfPossible() != null

    @ApiStatus.Internal
    @JvmStatic
    fun SearchEverywhereContributor<*>.unwrapFilesTabContributorIfPossible(): FilesTabSEContributor? =
      when (this) {
        is FilesTabSEContributor -> this
        is SearchEverywhereContributorWrapper -> this.getEffectiveContributor().unwrapFilesTabContributorIfPossible()
        else -> null
      }

    @ApiStatus.Internal
    @JvmStatic
    fun SearchEverywhereContributor<*>.isMainFilesContributor(): Boolean {
      return this is FileSearchEverywhereContributor || this is SearchEverywhereContributorWrapper && this.getEffectiveContributor()
        .isMainFilesContributor()
    }
  }
}

private val LOG = Logger.getInstance(NonIndexableFilesSEContributor::class.java)

@ApiStatus.Internal
@OptIn(ExperimentalAtomicApi::class)
class NonIndexableFilesSEContributor(event: AnActionEvent) : WeightedSearchEverywhereContributor<Any>,
                                                             DumbAware,
                                                             FilesTabSEContributor,
                                                             SearchEverywhereExtendedInfoProvider {
  override val project: Project = event.project!!
  private val navigationHandler: SearchEverywhereNavigationHandler = FileSearchEverywhereNavigationContributionHandler(project)

  private val scope: AtomicReference<ScopeDescriptor?> = AtomicReference(null)
  private val hiddenTypes: AtomicReference<Set<FileTypeRef>> = AtomicReference(emptySet())
  override val psiContext: SmartPsiElementPointer<PsiElement?>? = GotoActionBase.getPsiContext(event)?.let { context ->
    SmartPointerManager.getInstance(project).createSmartPsiElementPointer(context)
  }

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

  override fun setScope(scope: ScopeDescriptor) {
    this.scope.store(scope)
  }

  override fun setHiddenTypes(hiddenTypes: List<FileTypeRef>) {
    this.hiddenTypes.store(hiddenTypes.toSet())
  }

  /**
   * @implNote instead of running under one large read action, launches many short blocking RAs
   */
  override fun fetchWeightedElements(
    pattern: String,
    progressIndicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<Any>>,
  ) {
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

    val nameMatcher = NameUtil.buildMatcher("*$namePattern")
      .withMatchingMode(MatchingMode.IGNORE_CASE)
      .preferringStartMatches()
      .build()

    // search everywhere has limit of entries it allows contibutor to contribute.
    // We want to send good matches first, and only send others later if didn't find enough
    val suboptimalMatches = mutableListOf<VirtualFile>()

    val hiddenTypes = hiddenTypes.load()
    val filterByType = VirtualFileFilter { file -> FileTypeRef.forFileType(file.fileType) !in hiddenTypes }

    val scope = scope.load()?.scope
    val filter: VirtualFileFilter = if (scope == null) filterByType
    else filterByType.and { file -> scope.contains(file) }


    val filesDeque = ReadAction.nonBlocking<FilesDeque> {
      FilesDeque.nonIndexableDequeue(project, filter)
    }.executeSynchronously()
    while (true) {
      progressIndicator.checkCanceled()
      val file = filesDeque.computeNext()
      if (file == null) break

      val workspaceFileIndex = WorkspaceFileIndexEx.getInstance(project)
      val nonIndexableRoot = runReadActionBlocking { workspaceFileIndex.findNonIndexableFileSet(file) }?.root
      // path includes root
      val pathFromNonIndexableRoot = file.path.removePrefix(nonIndexableRoot?.parent?.path ?: "").removePrefix("/")

      if (!pathMatcher.matches(pathFromNonIndexableRoot)) {
        continue // file doesn't match pattern, skip
      }

      val matchingDegree = nameMatcher.matchingDegree(file.name)
      if (matchingDegree <= 0) {
        suboptimalMatches.add(file)
        continue // suboptimal match, process later, after "optimal" matches
      }

      val psiItem = PsiManager.getInstance(project).getPsiFileSystemItem(file) ?: continue
      val itemDescriptor = FoundItemDescriptor<Any>(psiItem, matchingDegree)
      if (!ReadAction.nonBlocking<Boolean> { consumer.process(itemDescriptor) }.executeSynchronously()) break
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
): WorkspaceFileSet? = findFileSet(file, true, false, true, false, false, true, false)

private fun PsiManager.getPsiFileSystemItem(file: VirtualFile): PsiFileSystemItem? = when {
  file.isDirectory -> ReadAction.nonBlocking<PsiDirectory?> { findDirectory(file) }.executeSynchronously()
  else -> ReadAction.nonBlocking<PsiFile?> { findFile(file) }.executeSynchronously()
}

private fun isGotoFileToNonIndexableEnabled(): Boolean = Registry.`is`("se.enable.non.indexable.files.contributor")
