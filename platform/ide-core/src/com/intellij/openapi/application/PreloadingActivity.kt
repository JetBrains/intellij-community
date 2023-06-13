// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.blockingContext
import org.jetbrains.annotations.ApiStatus.Internal

@Deprecated("Use ApplicationInitializedListener (asyncScope)")
@Internal
abstract class PreloadingActivity {
  open fun preload() {}

  /** Perform the preloading. */
  open suspend fun execute() : Unit = blockingContext {
    preload()
  }

  @Deprecated("Use {@link #execute()}", ReplaceWith("execute()"))
  open fun preload(@Suppress("unused") indicator: ProgressIndicator?) {
    preload()
  }
}