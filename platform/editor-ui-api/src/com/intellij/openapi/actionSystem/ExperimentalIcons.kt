// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

/**
 * Gate for the experimental icons pipeline (`com.intellij.platform.icons.IconDescriptor` descriptors rendered through the new icons API).
 *
 * When off, the IDE must look and behave exactly as before: descriptor suppliers are stored but never evaluated, and
 * [Presentation.getIconDescriptor] falls back to a descriptor bridged from the legacy Swing icon.
 */
@ApiStatus.Internal
object ExperimentalIcons {
  const val REGISTRY_KEY: String = "ide.experimental.icons"

  /**
   * Not cached on purpose: the key is `restartRequired`, and `Registry.is(key, default)` is safe to call before
   * `LoadingState.COMPONENTS_LOADED` (it returns the default), so a live read is both cheap and correct.
   */
  @JvmStatic
  val isEnabled: Boolean
    get() = Registry.`is`(REGISTRY_KEY, false)
}
