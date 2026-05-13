// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.util.PlatformUtils

internal object MinimapAvailability {
  fun isAvailable(): Boolean {
    return PlatformUtils.isPyCharm()
  }
}
