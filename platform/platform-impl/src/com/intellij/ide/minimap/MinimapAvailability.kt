// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.openapi.util.Key
import com.intellij.testFramework.TestModeFlags
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object MinimapAvailability {
  val FORCE_AVAILABLE: Key<Boolean> = Key.create("minimap.force.available.in.tests")

  fun isAvailable(): Boolean {
    return TestModeFlags.`is`(FORCE_AVAILABLE)
  }
}
