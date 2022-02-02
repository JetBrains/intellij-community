// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
sealed interface LinkResolveResult {

  companion object {

    /**
     * @return a result, which makes the browser load the documentation for the [target]
     */
    @RequiresReadLock(generateAssertion = false)
    @JvmStatic
    fun resolvedTarget(target: DocumentationTarget): LinkResolveResult {
      ApplicationManager.getApplication().assertReadAccessAllowed()
      return ResolvedTarget(target)
    }
  }
}
