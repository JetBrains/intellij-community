// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ComposePreviewClassLoaderProvider {
  /**
   * Provides a parent classloader for Compose UI previews that has access only to the platform classes.
   */
  fun getClassLoader(): ClassLoader = javaClass.classLoader
}
