// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find

import com.intellij.find.FindModel
import com.intellij.find.FindSettings
import com.intellij.find.findInProject.FindInProjectManager
import com.intellij.find.impl.FindAndReplaceExecutor
import com.intellij.find.impl.FindAndReplaceService
import com.intellij.find.impl.FindKey
import com.intellij.find.impl.FindPopupScopeUI
import com.intellij.find.replaceInProject.ReplaceInProjectManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
open class FindAndReplaceServiceImpl(val coroutineScope: CoroutineScope) : FindAndReplaceService {
  final override fun createExecutor(parentDisposable: Disposable, scopeUI: FindPopupScopeUI): FindAndReplaceExecutor {
    val childScope = coroutineScope.childScope("FindAndReplaceExecutorImpl")
    Disposer.register(parentDisposable) { childScope.cancel() }
    return createExecutor(childScope, scopeUI)
  }

  protected open fun createExecutor(coroutineScope: CoroutineScope, scopeUI: FindPopupScopeUI): FindAndReplaceExecutor {
    return FindAndReplaceExecutorImpl(coroutineScope, scopeUI)
  }

  override fun performFindAllOrReplaceAll(findModel: FindModel, project: Project) {
    if (FindKey.isEnabled) {
      coroutineScope.launch {
        FindInFilesApi.getInstance()
          .performFindAllOrReplaceAll(findModel, FindSettings.getInstance().isShowResultsInSeparateView, project.projectId())
      }
    }
    else {
      if (findModel.isReplaceState) {
        ReplaceInProjectManager.getInstance(project).replaceInPath(findModel)
      }
      else {
        FindInProjectManager.getInstance(project).findInPath(findModel)
      }
    }
  }
}
