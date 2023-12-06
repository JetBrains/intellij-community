// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.util.registry.Registry

private class MinimapConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable(): Configurable? {
    return if (Registry.`is`("editor.minimap.enabled", false)) MinimapConfigurable() else null
  }

  override fun canCreateConfigurable() = Registry.`is`("editor.minimap.enabled", false)
}