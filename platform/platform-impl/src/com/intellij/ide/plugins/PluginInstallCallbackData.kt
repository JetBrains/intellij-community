// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import java.nio.file.Path

data class PluginInstallCallbackData(
  val file: Path,
  val pluginDescriptor: IdeaPluginDescriptorImpl,
  val restartNeeded: Boolean,
)

data class PendingDynamicPluginInstall(
  val file: Path,
  val pluginDescriptor: IdeaPluginDescriptorImpl,
)
