// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.openapi.extensions.PluginDescriptor

internal val pluginsExplicitlyAllowedToExportOT = setOf(
  "org.jetbrains.toolbox-enterprise-client",
  "com.intellij.ml.llm"
)

internal fun PluginDescriptor.isAllowedToExportOT(): Boolean {
  return pluginId.idString in pluginsExplicitlyAllowedToExportOT
}