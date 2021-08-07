// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId

interface IdeaPluginDependency {
  val pluginId: PluginId

  val isOptional: Boolean
}