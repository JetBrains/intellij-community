// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation

import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
sealed interface LinkResult {

  companion object {

    /**
     * @return a result, which makes the browser load the documentation for the [target]
     */
    @JvmStatic
    fun resolvedTarget(target: DocumentationTarget): LinkResult {
      return ResolvedTarget(target)
    }
  }
}
