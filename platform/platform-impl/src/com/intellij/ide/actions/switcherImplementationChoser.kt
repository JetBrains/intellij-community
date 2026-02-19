// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.idea.AppMode
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun shouldUseFallbackSwitcher(): Boolean {
  val shouldUseFallbackSwitcher = Registry.`is`("switcher.use.fallback.in.monolith", false)
                                  && !PlatformUtils.isJetBrainsClient()
                                  && !AppMode.isRemoteDevHost()
  fileLogger().debug("Using Switcher ${if (shouldUseFallbackSwitcher) "fallback" else "split"} implementation")
  return shouldUseFallbackSwitcher
}