// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SearchEverywhereItemsProvider
import com.intellij.platform.searchEverywhere.SearchEverywhereItemsProviderFactory

class FileSearchEverywhereItemProviderFactory : SearchEverywhereItemsProviderFactory {
  override fun getItemsProvider(project: Project): SearchEverywhereItemsProvider {
    val legacyContributor = runBlockingCancellable {
      readAction {
        FileSearchEverywhereContributorFactory().createContributor(createActionEvent(project)) as SearchEverywhereAsyncContributor<Any?>
      }
    }

    return FileSearchEverywhereItemProvider(project, legacyContributor)
  }

  private fun createActionEvent(project: Project) = AnActionEvent.createFromDataContext("", null) {
    if (CommonDataKeys.PROJECT.`is`(it)) project
    else null
  }
}