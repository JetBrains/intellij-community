// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.impl.experiment

import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor

interface ABExperimentOption : PluginAware {
  val id: String

  fun getGroupCountForIde(isPopular: Boolean): Int

  fun getPluginDescriptor(): PluginDescriptor
}