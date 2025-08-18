// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.impl.RawNavigationRequest
import com.intellij.pom.AsyncNavigatable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class IdeNavigationServiceExecutor {
  companion object {
    fun getInstance(project: Project) = project.service<IdeNavigationServiceExecutor>()
  }

  open suspend fun navigate(request: RawNavigationRequest, requestFocus: Boolean) {
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