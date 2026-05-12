// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.model.MinimapFileSupportPolicy

internal object MinimapAvailability {
  fun isAvailable(): Boolean {
    return MinimapRegistry.isEnabled() || MinimapFileSupportPolicy.hasIndependentSupport()
  }
}
