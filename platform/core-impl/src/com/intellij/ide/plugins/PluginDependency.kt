// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PluginDependency : IdeaPluginDependency {
  val configFile: String?
  val subDescriptor: DependsSubDescriptor?
}