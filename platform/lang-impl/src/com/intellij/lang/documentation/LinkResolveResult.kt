// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation

import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
sealed interface LinkResolveResult {

  companion object {

    /**
     * @return a result, which makes the browser load the documentation for the [target]
     */
    @JvmStatic
    fun resolvedTarget(target: DocumentationTarget): LinkResolveResult {
      return ResolvedTarget(target)
    }
  }
}
