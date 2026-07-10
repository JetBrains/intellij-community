// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor

internal class IjentDashboardConfigurableProvider(private val project: Project) : ConfigurableProvider() {
  override fun canCreateConfigurable(): Boolean {
    return when {
      project.getEelDescriptor() is LocalEelDescriptor -> false
      else -> true
    }
  }

  override fun createConfigurable(): Configurable = IjentDashboardConfigurable(project)
}
