// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import javax.swing.JComponent

class InlaySettingsConfigurable(val project: Project) : Configurable {
  override fun createComponent(): JComponent? {
    return InlaySettingsPanel(project)
  }

  override fun isModified(): Boolean {
    return false
  }

  override fun apply() {
  }

  override fun getDisplayName(): String = CodeInsightBundle.message("settings.inlay.hints.panel.name")
}

class InlaySettingsConfigurableProvider(val project: Project): ConfigurableProvider() {
  override fun createConfigurable(): Configurable {
    return if (Registry.`is`("new.inlay.settings", false)) InlaySettingsConfigurable(project) else InlayHintsConfigurable(project)
  }
}