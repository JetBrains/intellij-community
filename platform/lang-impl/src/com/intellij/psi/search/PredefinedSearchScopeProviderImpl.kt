// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.usages.UsageViewManager
import kotlinx.coroutines.*

class PredefinedSearchScopeProviderImpl(project: Project) : PredefinedSearchScopeProviderBase(project),
                                                            Disposable {

  private val scope = CoroutineScope(SupervisorJob())

  init {
    Disposer.register(project, this)
  }

  override fun dispose() {
    scope.cancel()
  }

  override fun getPredefinedScopes(
    dataContext: DataContext?,
    suggestSearchInLibs: Boolean,
    prevSearchFiles: Boolean,
    currentSelection: Boolean,
    usageView: Boolean,
    showEmptyScopes: Boolean,
  ): Set<SearchScope> {
    val selectedTextEditor = selectedTextEditor

    val psiFile = selectedTextEditor?.document?.let {
      runReadAction {
        PsiDocumentManager.getInstance(project).getPsiFile(it)
      }
    }

    val result = mutableSetOf<SearchScope>()
    result += getPredefinedScopes(dataContext, suggestSearchInLibs, showEmptyScopes)

    val currentFile = runReadAction {
      fillFromDataContext(dataContext, result, psiFile)
    }

    val scopesFromUsageView = if (usageView)
      UsageViewManager.getInstance(project).selectedUsageView?.let {
        runReadAction {
          getScopesFromUsageView(it, prevSearchFiles)
        }
      } ?: emptySet()
    else
      emptySet()

    val selectedFilesScope = runReadAction {
      getSelectedFilesScope(dataContext, currentFile)
    }

    val context = ScopeCollectionContext(
      psiFile,
      selectedTextEditor,
      scopesFromUsageView,
      currentFile,
      selectedFilesScope,
    )

    result += runReadAction {
      context.collectRestScopes(project, currentSelection, usageView, showEmptyScopes)
    }
    return result
  }

  override suspend fun getPredefinedScopesAsync(
    dataContext: DataContext?,
    suggestSearchInLibs: Boolean,
    prevSearchFiles: Boolean,
    currentSelection: Boolean,
    usageView: Boolean,
    showEmptyScopes: Boolean,
  ): Set<SearchScope> = withContext(scope.coroutineContext) {
    val selectedTextEditor = selectedTextEditor

    val psiFile = selectedTextEditor?.document?.let {
      readAction {
        PsiDocumentManager.getInstance(project).getPsiFile(it)
      }
    }

    val result = mutableSetOf<SearchScope>()
    result += getPredefinedScopes(dataContext, suggestSearchInLibs, showEmptyScopes)

    val currentFile = readAction {
      fillFromDataContext(dataContext, result, psiFile)
    }

    val scopesFromUsageView = if (usageView)
      UsageViewManager.getInstance(project).selectedUsageView?.let {
        readAction {
          getScopesFromUsageView(it, prevSearchFiles)
        }
      } ?: emptySet()
    else
      emptySet()

    val selectedFilesScope = readAction {
      getSelectedFilesScope(dataContext, currentFile)
    }

    val context = ScopeCollectionContext(
      psiFile,
      selectedTextEditor,
      scopesFromUsageView,
      currentFile,
      selectedFilesScope,
    )

    result += readAction {
      context.collectRestScopes(project, currentSelection, usageView, showEmptyScopes)
    }
    result
  }

  private val selectedTextEditor
    get() = if (ApplicationManager.getApplication().isDispatchThread)
      FileEditorManager.getInstance(project).selectedTextEditor
    else
      null
}