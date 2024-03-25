// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.impl.experiment

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnableStateChangedListener
import com.intellij.ide.plugins.PluginStateListener
import com.intellij.openapi.components.service

internal class ABExperimentPluginTracker : PluginStateListener, PluginEnableStateChangedListener {
  override fun stateChanged(pluginDescriptors: Collection<IdeaPluginDescriptor>, enable: Boolean) {
    if (!enable) {
      return
    }

    service<ABExperimentGroupStorageService>().setupNewPluginABExperimentOptions()
  }

  override fun install(descriptor: IdeaPluginDescriptor) {
    service<ABExperimentGroupStorageService>().setupNewPluginABExperimentOptions()
  }

  override fun uninstall(descriptor: IdeaPluginDescriptor) {
    service<ABExperimentGroupStorageService>().setupNewPluginABExperimentOptions()
  }
}