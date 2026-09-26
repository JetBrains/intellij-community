// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.debug

import com.intellij.ide.ApplicationActivity

/**
 * Ensures [ImageDebugUtil] is loaded on IDE startup, because debugger renderers later resolve and invoke its static methods by FQN.
 */
internal class ImageDebugUtilLoader : ApplicationActivity {
  override suspend fun execute() {
    ImageDebugUtil.ensureLoaded()
  }
}
