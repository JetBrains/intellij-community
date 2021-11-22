// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation

import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
class LinkResult private constructor(
  internal val target: DocumentationTarget,
) {

  companion object {

    @JvmStatic
    fun resolvedTarget(target: DocumentationTarget): LinkResult = LinkResult(target)
  }
}
