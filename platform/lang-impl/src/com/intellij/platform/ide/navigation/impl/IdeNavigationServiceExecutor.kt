// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation.impl

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.impl.RawNavigationRequest
import com.intellij.util.concurrency.annotations.RequiresBlockingContext

open class IdeNavigationServiceExecutor {
  companion object {
    @RequiresBlockingContext
    fun getInstance(project: Project) = project.service<IdeNavigationServiceExecutor>()
  }

  open suspend fun navigate(request: RawNavigationRequest, requestFocus: Boolean) {
    blockingContext {
      request.navigatable.navigate(requestFocus)
    }
  }
}