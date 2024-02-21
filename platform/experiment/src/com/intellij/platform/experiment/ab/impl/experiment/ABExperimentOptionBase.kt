// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.impl.experiment

import com.intellij.openapi.extensions.PluginDescriptor

abstract class ABExperimentOptionBase : ABExperimentOption {

  private lateinit var pluginDescriptor: PluginDescriptor

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }

  override fun getPluginDescriptor(): PluginDescriptor {
    return pluginDescriptor
  }

}