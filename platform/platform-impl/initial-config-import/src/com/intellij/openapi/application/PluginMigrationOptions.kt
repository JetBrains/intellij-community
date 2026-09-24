// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import java.nio.file.Path

@Suppress("UNUSED_PARAMETER")
internal class PluginMigrationOptions(
  val previousVersion: String?,
  val currentProductVersion: String,
  val newConfigDir: Path,
  val oldConfigDir: Path,
  val pluginsToMigrate: MutableList<IdeaPluginDescriptor>,
  val pluginsToDownload: MutableList<IdeaPluginDescriptor>,
  val pluginsToDisable: MutableList<PluginId>,
  val log: Logger
)
