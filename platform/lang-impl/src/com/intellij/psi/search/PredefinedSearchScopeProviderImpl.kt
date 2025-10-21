// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search

import com.intellij.find.FindModel
import com.intellij.find.impl.FindInProjectExtension
import com.intellij.ide.IdeBundle
import com.intellij.ide.hierarchy.HierarchyBrowserBase
import com.intellij.ide.scratch.ScratchesSearchScope
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.OpenFilesScope
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbUnawareHider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.scope.EditorSelectionLocalSearchScope
import com.intellij.psi.util.PsiUtilCore
import com.intellij.usages.Usage
import com.intellij.usages.UsageView
import com.intellij.usages.UsageViewManager
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.util.PlatformUtils
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.JBIterable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

// used by Rider
open class PredefinedSearchScopeProviderImpl : PredefinedSearchScopeProvider() {
  override fun getPredefinedScopes(project: Project,
                                   dataContext: DataContext?,
                                   suggestSearchInLibs: Boolean,
                                   prevSearchFiles: Boolean,
                                   currentSelection: Boolean,
                                   usageView: Boolean,
                                   showEmptyScopes: Boolean): List<SearchScope> {
    val context = SlowOperations.knownIssue("IDEA-349676, EA-696744").use {
      ScopeCollectionContext.collectContext(
        project, dataContext, suggestSearchInLibs, prevSearchFiles, usageView, showEmptyScopes)
    }
    val result = context.result
    result.addAll(context.collectRestScopes(project, currentSelection, usageView, showEmptyScopes))
    return ArrayList(result)
  }

  override fun getPredefinedScopesAsync(project: Project,
                                        dataContext: DataContext?,
                                        suggestSearchInLibs: Boolean,
                                        prevSearchFiles: Boolean,
                                        currentSelection: Boolean,
                                        usageView: Boolean,
                                        showEmptyScopes: Boolean): Promise<List<SearchScope>> {
    val context = SlowOperations.knownIssue("IDEA-345912, EA-1076769").use {
      ScopeCollectionContext.collectContext(
        project, dataContext, suggestSearchInLibs, prevSearchFiles, usageView, showEmptyScopes)
    }

    val promise = AsyncPromise<List<SearchScope>>()
    ReadAction.nonBlocking<Collection<SearchScope>>()
    { context.collectRestScopes(project, currentSelection, usageView, showEmptyScopes) }
      .expireWith(project)
      .finishOnUiThread(ModalityState.any()) { backgroundResult: Collection<SearchScope>? ->
        val result = context.result
        result.addAll(backgroundResult!!)
        promise.setResult(ArrayList(result))
      }
      .submit(AppExecutorUtil.getAppExecutorService())

    return promise
  }

  override suspend fun getPredefinedScopesSuspend(
    project: Project,
    dataContext: DataContext?,
    suggestSearchInLibs: Boolean,
    prevSearchFiles: Boolean,
    currentSelection: Boolean,
    usageView: Boolean,
    showEmptyScopes: Boolean,
  ): List<SearchScope> {
    val context = ScopeCollectionContext.collectContextSuspend(
      project, dataContext, suggestSearchInLibs, prevSearchFiles, usageView, showEmptyScopes
    )
    val restScopes = readAction {
      context.collectRestScopes(project, currentSelection, usageView, showEmptyScopes)
    }
    return context.result + restScopes
  }

  private data class ScopeCollectionContext(val psiFile: PsiFile?,
                                            val selectedTextEditor: Editor?,
                                            val scopesFromUsageView: Collection<SearchScope>,
                                            val currentFile: PsiFile?,
                                            val selectedFilesScope: SearchScope?,
                                            val result: MutableCollection<SearchScope>) {
    // under read action
    fun collectRestScopes(project: Project,
                          currentSelection: Boolean,
                          usageView: Boolean,
                          showEmptyScopes: Boolean): Collection<SearchScope> {
      if (project.isDisposed() || psiFile != null && !psiFile.isValid() || selectedTextEditor != null && selectedTextEditor.isDisposed()) {
        return emptyList()
      }

      val backgroundResult = mutableSetOf<SearchScope>()

      if (currentFile != null || showEmptyScopes) {
        val scope = if (currentFile != null) arrayOf<PsiElement>(currentFile) else PsiElement.EMPTY_ARRAY
        backgroundResult.add(LocalSearchScope(scope, getCurrentFileScopeName()))
      }

      if (currentSelection && selectedTextEditor != null && psiFile != null) {
        val selectionModel = selectedTextEditor.getSelectionModel()
        if (selectionModel.hasSelection()) {
          backgroundResult.add(EditorSelectionLocalSearchScope(selectedTextEditor, project, IdeBundle.message("scope.selection")))
        }
      }

      if (usageView) {
        addHierarchyScope(project, backgroundResult)
        backgroundResult.addAll(scopesFromUsageView)
      }

      ContainerUtil.addIfNotNull(backgroundResult, selectedFilesScope)

      return backgroundResult
    }

    companion object {

      suspend fun collectContextSuspend(
        project: Project,
        dataContext: DataContext?,
        suggestSearchInLibs: Boolean,
        prevSearchFiles: Boolean,
        usageView: Boolean,
        showEmptyScopes: Boolean,
      ): ScopeCollectionContext {
        val result: MutableCollection<SearchScope> = LinkedHashSet()

        readAction {
          addCommonScopes(result, project, suggestSearchInLibs, dataContext, showEmptyScopes)
        }

        val scopesFromUsageView = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          if (usageView) getScopesFromUsageViewSuspend(project, prevSearchFiles) else emptyList()
        }

        val selectedTextEditor = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          FileEditorManager.getInstance(project).getSelectedTextEditor()
        }

        return readAction {
          val psiFile = selectedTextEditor?.let {
            PsiDocumentManager.getInstance(project).getPsiFile(it.getDocument())
          }
          val currentFile = fillFromDataContext(dataContext, result, psiFile)
          val selectedFilesScope = getSelectedFilesScope(project, dataContext, currentFile)
          ScopeCollectionContext(psiFile, selectedTextEditor, scopesFromUsageView, currentFile, selectedFilesScope, result)
        }
      }

      // in EDT
      fun collectContext(project: Project,
                         dataContext: DataContext?,
                         suggestSearchInLibs: Boolean,
                         prevSearchFiles: Boolean,
                         usageView: Boolean,
                         showEmptyScopes: Boolean): ScopeCollectionContext {
        val result: MutableCollection<SearchScope> = LinkedHashSet()

        addCommonScopes(result, project, suggestSearchInLibs, dataContext, showEmptyScopes)

        val selectedTextEditor = if (ApplicationManager.getApplication().isDispatchThread())
          FileEditorManager.getInstance(project).getSelectedTextEditor()
        else null

        val psiFile = selectedTextEditor?.let {
          PsiDocumentManager.getInstance(project).getPsiFile(it.getDocument())
        }
        val currentFile = fillFromDataContext(dataContext, result, psiFile)
        val selectedFilesScope = getSelectedFilesScope(project, dataContext, currentFile)

        val scopesFromUsageView = if (usageView) getScopesFromUsageView(project, prevSearchFiles) else emptyList()

        return ScopeCollectionContext(psiFile, selectedTextEditor, scopesFromUsageView, currentFile, selectedFilesScope, result)
      }

      private fun addCommonScopes(
        result: MutableCollection<SearchScope>,
        project: Project,
        suggestSearchInLibs: Boolean,
        dataContext: DataContext?,
        showEmptyScopes: Boolean,
      ) {
        addGlobalScopes(result, project, suggestSearchInLibs)
        addExtensionScopes(dataContext, project, result)
        addTestAndScratchesScopes(project, result)
        addRecentFilesScope(project, result, showEmptyScopes)
        addRecentlyModifiedFilesScope(project, result, showEmptyScopes)
        addOpenFilesScope(project, result, showEmptyScopes)
      }

      private fun addGlobalScopes(
        result: MutableCollection<SearchScope>,
        project: Project,
        suggestSearchInLibs: Boolean,
      ) {
        result.add(GlobalSearchScope.everythingScope(project))
        result.add(GlobalSearchScope.projectScope(project))
        if (suggestSearchInLibs) {
          result.add(GlobalSearchScope.allScope(project))
        }
      }

      private fun addExtensionScopes(
        dataContext: DataContext?,
        project: Project,
        result: MutableCollection<SearchScope>,
      ) {
        val adjustedContext = dataContext ?: SimpleDataContext.getProjectContext(project)
        for (each in SearchScopeProvider.EP_NAME.extensionList) {
          runCatching {
            result.addAll(each.getGeneralSearchScopes(project, adjustedContext))
          }.getOrHandleException {
            LOG.error("Couldn't retrieve general scopes from $each", it)
          }
        }
      }

      private fun addTestAndScratchesScopes(project: Project,
                                            result: MutableCollection<SearchScope>) {
        if (ModuleUtil.hasTestSourceRoots(project)) {
          result.add(GlobalSearchScopesCore.projectProductionScope(project))
          result.add(GlobalSearchScopesCore.projectTestScope(project))
        }
        result.add(ScratchesSearchScope.getScratchesScope(project))
      }

      private fun addRecentFilesScope(project: Project,
                                      result: MutableCollection<SearchScope>,
                                      showEmptyScopes: Boolean) {
        val recentFilesScope = recentFilesScope(project, false)
        if (!SearchScope.isEmptyScope(recentFilesScope)) {
          result.add(recentFilesScope)
        }
        else if (showEmptyScopes) {
          result.add(LocalSearchScope(PsiElement.EMPTY_ARRAY, getRecentlyViewedFilesScopeName()))
        }
      }

      private fun addRecentlyModifiedFilesScope(project: Project,
                                                result: MutableCollection<SearchScope>,
                                                showEmptyScopes: Boolean) {
        val recentModFilesScope = recentFilesScope(project, true)
        ContainerUtil.addIfNotNull(
          result, if (!SearchScope.isEmptyScope(recentModFilesScope)) recentModFilesScope
        else if (showEmptyScopes) LocalSearchScope(
          PsiElement.EMPTY_ARRAY, getRecentlyChangedFilesScopeName())
        else null)
      }

      private fun addOpenFilesScope(project: Project,
                                    result: MutableCollection<SearchScope>,
                                    showEmptyScopes: Boolean) {
        val openFilesScope = GlobalSearchScopes.openFilesScope(project)
        ContainerUtil.addIfNotNull(
          result, if (openFilesScope !== GlobalSearchScope.EMPTY_SCOPE) openFilesScope
        else if (showEmptyScopes) LocalSearchScope(
          PsiElement.EMPTY_ARRAY, OpenFilesScope.getNameText())
        else null)
      }
    }
  }
  
  internal class SelectedFilesFindInProjectExtension : FindInProjectExtension {

    override fun initModelFromContext(model: FindModel, dataContext: DataContext): Boolean {
      val virtualFiles = dataContext.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
      if (virtualFiles != null && virtualFiles.size > 1) {
        val scope = SelectedFilesScope(null, *virtualFiles)
        model.isCustomScope = true
        model.customScope = scope
        model.customScopeName = scope.displayName
      }
      return false
    }
  }

  internal class SelectedFilesScope(project: Project?, vararg filesOrDirs: VirtualFile) : GlobalSearchScope(project) {
    private val myFiles: MutableSet<VirtualFile> = HashSet()
    private val myDirectories: MutableSet<VirtualFile> = HashSet()

    init {
      require(filesOrDirs.isNotEmpty())

      for (fileOrDir in filesOrDirs) {
        if (fileOrDir.isDirectory()) {
          myDirectories.add(fileOrDir)
        }
        else {
          myFiles.add(fileOrDir)
        }
      }
    }

    override fun isSearchInModuleContent(aModule: Module): Boolean {
      return true
    }

    override fun isSearchInLibraries(): Boolean {
      return true
    }

    override fun contains(file: VirtualFile): Boolean {
      if (myFiles.contains(file)) return true
      return VfsUtilCore.isUnder(file, myDirectories)
    }

    override fun getDisplayName(): String {
      if (myFiles.isEmpty()) {
        return IdeBundle.message("scope.selected.directories", myDirectories.size)
      }

      if (myDirectories.isEmpty()) {
        return IdeBundle.message("scope.selected.files", myFiles.size)
      }

      return IdeBundle.message("scope.selected.files.and.directories", myFiles.size, myDirectories.size)
    }
  }

  companion object {
    @JvmStatic
    fun getRecentlyViewedFilesScopeName(): @Nls String = IdeBundle.message("scope.recent.files")
    @JvmStatic
    fun getRecentlyChangedFilesScopeName(): @Nls String = IdeBundle.message("scope.recent.modified.files")
    @JvmStatic
    fun getCurrentFileScopeName(): @Nls String = IdeBundle.message("scope.current.file")

    // in EDT
    private fun getScopesFromUsageView(project: Project, prevSearchFiles: Boolean): Collection<SearchScope> {
      val selectedUsageView = getSelectedAndCompletedUsageView(project) ?: return emptyList()
      val scopes = LinkedHashSet<SearchScope>()
      addPreviousSearchScopes(selectedUsageView, prevSearchFiles, project, scopes)
      return scopes
    }

    private suspend fun getScopesFromUsageViewSuspend(project: Project, prevSearchFiles: Boolean): Collection<SearchScope> {
      val selectedUsageView = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        getSelectedAndCompletedUsageView(project)
      } ?: return emptyList()
      val scopes = LinkedHashSet<SearchScope>()
      readAction {
        addPreviousSearchScopes(selectedUsageView, prevSearchFiles, project, scopes)
      }
      return scopes
    }

    private fun getSelectedAndCompletedUsageView(project: Project): UsageView? =
      UsageViewManager.getInstance(project).getSelectedUsageView()?.takeIf { !it.isSearchInProgress }

    private fun addPreviousSearchScopes(
      selectedUsageView: UsageView,
      prevSearchFiles: Boolean,
      project: Project,
      scopes: LinkedHashSet<SearchScope>,
    ) {
      val usages: MutableSet<Usage> = selectedUsageView.getUsages().toMutableSet()
      usages.removeAll(selectedUsageView.getExcludedUsages())

      if (prevSearchFiles) {
        val files = collectFiles(usages, true)
        if (!files.isEmpty()) {
          val prev: GlobalSearchScope = object : GlobalSearchScope(project) {
            private var myFiles: Set<VirtualFile>? = null
            override fun getDisplayName(): String {
              return IdeBundle.message("scope.files.in.previous.search.result")
            }

            @Synchronized
            override fun contains(file: VirtualFile): Boolean {
              if (myFiles == null) {
                myFiles = collectFiles(usages, false)
              }
              return myFiles!!.contains(file)
            }

            override fun isSearchInModuleContent(aModule: Module): Boolean {
              return true
            }

            override fun isSearchInLibraries(): Boolean {
              return true
            }
          }
          scopes.add(prev)
        }
      }
      else {
        val results: MutableList<PsiElement> = ArrayList(usages.size)
        for (usage in usages) {
          if (usage is PsiElementUsage) {
            val element = usage.getElement()
            if (element != null && element.isValid() && element.getContainingFile() != null) {
              results.add(element)
            }
          }
        }

        if (!results.isEmpty()) {
          scopes.add(LocalSearchScope(PsiUtilCore.toPsiElementArray(results), IdeBundle.message("scope.previous.search.results")))
        }
      }
    }

    private fun fillFromDataContext(dataContext: DataContext?,
                                    result: MutableCollection<SearchScope>,
                                    psiFile: PsiFile?): PsiFile? {
      var currentFile = psiFile
      if (dataContext != null) {
        var dataContextElement: PsiElement? = CommonDataKeys.PSI_FILE.getData(dataContext)
        if (dataContextElement == null) {
          dataContextElement = CommonDataKeys.PSI_ELEMENT.getData(dataContext)
        }

        if (dataContextElement == null && psiFile != null) {
          dataContextElement = psiFile
        }

        if (dataContextElement != null) {
          if (!PlatformUtils.isCidr() && !PlatformUtils.isRider()) { // TODO: have an API to disable module scopes.
            var module = ModuleUtilCore.findModuleForPsiElement(dataContextElement)
            if (module == null) {
              module = PlatformCoreDataKeys.MODULE.getData(dataContext)
            }
            if (module != null && !ModuleType.isInternal(module)) {
              result.add(module.getModuleScope())
            }
          }
          if (currentFile == null) {
            currentFile = dataContextElement.getContainingFile()
          }
        }
      }

      return currentFile
    }

    private fun addHierarchyScope(project: Project, result: MutableCollection<in SearchScope>) {
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.HIERARCHY)
      if (toolWindow == null) {
        return
      }

      val contentManager = toolWindow.getContentManager()
      val content = contentManager.getSelectedContent()
      if (content == null) {
        return
      }

      val name = content.getDisplayName()
      var component = content.getComponent()
      if (component is DumbUnawareHider) {
        component = component.content
      }

      val hierarchyBrowserBase = component as? HierarchyBrowserBase
      val elements = hierarchyBrowserBase?.getAvailableElements()
      if (!elements.isNullOrEmpty()) {
        result.add(LocalSearchScope(elements, LangBundle.message("predefined.search.scope.hearchy.scope.display.name", name)))
      }
    }

    fun recentFilesScope(project: Project, changedOnly: Boolean): SearchScope {
      val name = if (changedOnly) getRecentlyChangedFilesScopeName() else getRecentlyViewedFilesScopeName()

      val files = if (changedOnly)
        IdeDocumentHistory.getInstance(project).getChangedFiles()
      else
        JBIterable.from(EditorHistoryManager.getInstance(project).fileList)
          .append(FileEditorManager.getInstance(project).getOpenFiles()).unique().toList()

      return if (files.isEmpty()) LocalSearchScope.EMPTY else GlobalSearchScope.filesScope(project, files, name)
    }

    @JvmStatic
    fun getSelectedFilesScope(project: Project,
                              dataContext: DataContext?,
                              currentFile: PsiFile?): SearchScope? {
      val filesOrDirs = if (dataContext == null) null else CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)
      if (filesOrDirs.isNullOrEmpty()) {
        return null
      }

      if (filesOrDirs.size == 1 && currentFile != null && filesOrDirs[0] == currentFile.getVirtualFile()) {
        return null
      }

      return SelectedFilesScope(project, *filesOrDirs)
    }

    private fun collectFiles(usages: Set<Usage>, findFirst: Boolean): Set<VirtualFile> {
      val files: MutableSet<VirtualFile> = HashSet()

      for (usage in usages) {
        if (usage is PsiElementUsage) {
          val psiElement = usage.getElement()
          if (psiElement != null && psiElement.isValid()) {
            val psiFile = psiElement.getContainingFile()
            if (psiFile != null) {
              val file = psiFile.getVirtualFile()
              if (file != null) {
                files.add(file)
                if (findFirst) return files
              }
            }
          }
        }
      }

      return files
    }
  }
}

private val LOG = logger<PredefinedSearchScopeProviderImpl>()
