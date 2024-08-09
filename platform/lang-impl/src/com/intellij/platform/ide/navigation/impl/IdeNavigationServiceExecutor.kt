// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation.impl

import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.impl.RawNavigationRequest
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class IdeNavigationServiceExecutor {
  companion object {
    @RequiresBlockingContext
    fun getInstance(project: Project) = project.service<IdeNavigationServiceExecutor>()
  }

  open suspend fun navigate(request: RawNavigationRequest, requestFocus: Boolean) {
    val navigatable = request.navigatable
    if (navigatable is PsiFileNode) {
      navigatable.navigateAsync(requestFocus)
    }
    else {
      withContext(Dispatchers.EDT) {
        blockingContext {
          //readaction is not enough
          WriteIntentReadAction.run {
            navigatable.navigate(requestFocus)
          }
        }
      }
    }
  }
}