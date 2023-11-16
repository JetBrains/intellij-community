// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation.finder

import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.SymbolSearchEverywhereContributor
import com.intellij.navigation.NavigationKeyPrefix
import com.intellij.navigation.getNavigationKeyValue
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.withRawProgressReporter
import com.intellij.psi.PsiElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PsiElementFinder(
    private val fqnKey: NavigationKeyPrefix = NavigationKeyPrefix.FQN,
    private val fragmentKey: NavigationKeyPrefix = NavigationKeyPrefix.FRAGMENT
) {
  sealed interface FindResult {
    class Success(val psiElement: PsiElement) : FindResult

    class Error(val message: String) : FindResult
  }

  suspend fun find(project: Project, parameters: Map<String, String?>): FindResult {
    val fqn =
        parameters.getNavigationKeyValue(fqnKey)
            ?: return FindResult.Error(
                IdeBundle.message("jb.protocol.navigate.missing.parameter", fqnKey))
    val fragment = parameters.getNavigationKeyValue(fragmentKey)

    val fqnWitFragment = fragment?.let { "$fqn#$it" } ?: fqn

    val element =
        withBackgroundProgress(
            project,
            IdeBundle.message("navigate.command.search.reference.progress.title", fragment)) {
              val dataContext = SimpleDataContext.getProjectContext(project)
              val searcher =
                  withContext(Dispatchers.EDT) {
                    SymbolSearchEverywhereContributor(
                        AnActionEvent.createFromDataContext(
                            ActionPlaces.UNKNOWN, null, dataContext))
                  }
              Disposer.register(project, searcher)
              try {
                withContext(Dispatchers.Default) {
                  withRawProgressReporter {
                    coroutineToIndicator {
                      val wrapperIndicator =
                          SensitiveProgressWrapper(ProgressManager.getGlobalProgressIndicator())
                      searcher
                          .search(fqnWitFragment, wrapperIndicator)
                          .asSequence()
                          .filterIsInstance<PsiElement>()
                          .firstOrNull()
                    }
                  }
                }
              } finally {
                Disposer.dispose(searcher)
              }
            }
            ?: return FindResult.Error(
                IdeBundle.message("jb.protocol.navigate.problem.psi.element", fqn))

    return FindResult.Success(element)
  }
}
