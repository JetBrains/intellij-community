// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.fuzzyMatching

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GotoFileFuzzyMatcherFactory {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<GotoFileFuzzyMatcherFactory> =
      ExtensionPointName.create("com.intellij.gotoFileFuzzyMatcherFactory")
  }

  fun createMatcher(pattern: String): GotoFileFuzzyMatcher?
}
