// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.rendering

import org.jetbrains.icons.ExperimentalIconsApi

@ExperimentalIconsApi
sealed interface LoadingStrategy {
  class RenderBlank(
    val dimensions: Dimensions
  ) : LoadingStrategy

  class RenderPlaceholder(
    val placeHolder: IconRenderer
  ) : LoadingStrategy

  object BlockThread : LoadingStrategy
}