// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.NavigationHistoryContext
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.impl.RawNavigationRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
class IdeNavigationServiceExecutor {
  companion object {
    fun getInstance(project: Project): IdeNavigationServiceExecutor = project.service<IdeNavigationServiceExecutor>()
  }

  suspend fun navigate(request: RawNavigationRequest, requestFocus: Boolean) {
    val navigatable = request.navigatable
    if (navigatable is AsyncNavigatable) {
      navigatable.navigateAsync(requestFocus)
    }
    else {
      withContext(Dispatchers.EDT) {
        //readaction is not enough
        WriteIntentReadAction.run {
          navigatable.navigate(requestFocus)
        }
      }
    }
  }
}

/**
 * Runs [action] and records its navigation side effect in [IdeDocumentHistory]:
 * the current place is captured before the action starts and is committed as a back-history entry
 * after the action completes.
 *
 * NB: failed and no-op navigation leaves no history entry.
 * Command-based history recording is suppressed only for commands executed by [action], so unrelated
 * navigation commands may still contribute their own history entries while [action] is suspended.
 */
@ApiStatus.Internal
suspend fun <T> performNavigationHistoryAware(project: Project, action: suspend () -> T): T {
  // Keep creation and processing atomic with respect to caller cancellation
  val commitAfterNavigation = withContext(Dispatchers.EDT + NonCancellable) {
    project.serviceAsync<IdeDocumentHistory>().prepareHistorySnapshot()
  }

  return try {
    withContext(NavigationHistoryContext.withContextElement(commitAfterNavigation)) {
      action()
    }
  } finally {
    withContext(Dispatchers.EDT + NonCancellable) {
      commitAfterNavigation.commitIfChanged()
    }
  }
}
