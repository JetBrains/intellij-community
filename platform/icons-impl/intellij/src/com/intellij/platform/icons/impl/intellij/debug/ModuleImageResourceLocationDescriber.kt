// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.debug

import com.intellij.platform.icons.impl.intellij.ModuleImageResourceLocation
import com.intellij.platform.icons.impl.rendering.debug.ImageResourceLocationDescriber
import com.intellij.platform.icons.impl.rendering.debug.ImageResourceLocationDescribers
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Registers the describers for the IntelliJ image resource locations, so icon debugging tools can show their fields.
 * Kept in an internal object (no file facade) so nothing here reaches the module's API dump.
 */
internal object IntelliJImageResourceLocationDescribers {
  private val registered = AtomicBoolean()

  /** Idempotent. */
  fun register() {
    if (registered.compareAndSet(false, true)) {
      ImageResourceLocationDescribers.register(ModuleImageResourceLocationDescriber)
    }
  }
}

internal object ModuleImageResourceLocationDescriber : ImageResourceLocationDescriber<ModuleImageResourceLocation> {
  override val locationClass: Class<ModuleImageResourceLocation> = ModuleImageResourceLocation::class.java

  override fun describe(location: ModuleImageResourceLocation): List<Pair<String, String?>> = listOf(
    "path" to location.path,
    "pluginId" to location.pluginId,
    "moduleId" to location.moduleId,
  )
}
