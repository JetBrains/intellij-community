// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UIUtils")

package com.intellij.ide.plugins.newui

import com.intellij.icons.AllIcons
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnabledState
import javax.swing.Icon

fun perProjectIcon(state: PluginEnabledState): Icon? =
  if (state.isPerProject)
    AllIcons.General.ProjectConfigurable
  else
    null

fun MyPluginModel.changeEnableDisable(plugins: Set<IdeaPluginDescriptor>,
                                      enabled: Boolean) =
  if (enabled) {
    enablePlugins(plugins)
  }
  else {
    disablePlugins(plugins)
  }