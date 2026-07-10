// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.TestModeFlags
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object MinimapAvailability {
  val FORCE_AVAILABLE: Key<Boolean> = Key.create("minimap.force.available.in.tests")
  private const val REGISTRY_KEY: String = "ide.minimap.available"

  fun isAvailable(): Boolean {
    return TestModeFlags.`is`(FORCE_AVAILABLE) ||
           (PlatformUtils.isPyCharm() && Registry.`is`(REGISTRY_KEY))
  }
}
