// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit

import com.intellij.openapi.wm.impl.FrameInfo
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class LightEditConfiguration {
  @Internal
  enum class PreferredMode {
    LightEdit,
    Project
  }

  var autosaveMode: Boolean = false
  var sessionFiles: List<String> = emptyList()

  var frameInfo: FrameInfo? = null

  var preferredMode: PreferredMode? = null
}
