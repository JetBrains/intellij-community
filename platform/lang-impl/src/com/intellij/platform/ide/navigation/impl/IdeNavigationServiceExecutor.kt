package com.intellij.platform.ide.navigation.impl

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.impl.RawNavigationRequest

open class IdeNavigationServiceExecutor {

  companion object {
    fun getInstance(project: Project) = project.service<IdeNavigationServiceExecutor>()
  }

  open suspend fun navigate(request: RawNavigationRequest, requestFocus: Boolean) {
    blockingContext {
      request.navigatable.navigate(requestFocus)
    }
  }
}